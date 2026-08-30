package uz.ithunter.crm.casemodule.engine.condition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.engine.port.PaymentState;
import uz.ithunter.crm.casemodule.engine.port.PaymentStateProvider;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * TEST_MATRIX.md U-11, U-12. Plain JUnit, no Spring context - {@link PaymentStateSatisfiedConditionHandler}
 * takes its {@link PaymentStateProvider} as an {@code ObjectProvider}, so a hand-built one over a
 * lambda is enough (same style {@code SiblingStageLookup} lets U-10 test the parallel gate).
 */
class PaymentStateSatisfiedConditionHandlerTest {

    private ObjectProvider<PaymentStateProvider> providerOf(PaymentState state) {
        return new ObjectProvider<>() {
            @Override
            public PaymentStateProvider getObject() {
                return caseId -> state;
            }

            @Override
            public PaymentStateProvider getIfAvailable() {
                return caseId -> state;
            }

            @Override
            public PaymentStateProvider getObject(Object... args) {
                return getObject();
            }
        };
    }

    private Workflow workflow(boolean allowExecutionBeforeFullPayment) {
        Workflow workflow = new Workflow();
        workflow.setAllowExecutionBeforeFullPayment(allowExecutionBeforeFullPayment);
        return workflow;
    }

    private WorkflowTransition transition() {
        WorkflowTransition transition = new WorkflowTransition();
        transition.setConditionType(ConditionType.PAYMENT_STATE_SATISFIED);
        return transition;
    }

    private TransitionContext context(Workflow workflow) {
        return new TransitionContext(new ElectronicCase(), workflow, group -> java.util.List.of());
    }

    /** U-11: PARTIALLY_PAID does not satisfy the gate when the route forbids early execution. */
    @Test
    void partiallyPaidDoesNotSatisfyTheGateWhenEarlyExecutionIsForbidden() {
        var handler = new PaymentStateSatisfiedConditionHandler(
                providerOf(new PaymentState(false, true)));

        boolean matches = handler.matches(transition(), context(workflow(false)));

        assertThat(matches).isFalse();
    }

    /** U-12: the same evaluator is true on PARTIALLY_PAID when the route allows early execution. */
    @Test
    void partiallyPaidSatisfiesTheGateWhenEarlyExecutionIsAllowed() {
        var handler = new PaymentStateSatisfiedConditionHandler(
                providerOf(new PaymentState(false, true)));

        boolean matches = handler.matches(transition(), context(workflow(true)));

        assertThat(matches).isTrue();
    }

    @Test
    void fullyPaidAlwaysSatisfiesTheGateRegardlessOfTheRouteFlag() {
        var handler = new PaymentStateSatisfiedConditionHandler(
                providerOf(new PaymentState(true, false)));

        assertThat(handler.matches(transition(), context(workflow(false)))).isTrue();
        assertThat(handler.matches(transition(), context(workflow(true)))).isTrue();
    }

    @Test
    void unpaidNeverSatisfiesTheGate() {
        var handler = new PaymentStateSatisfiedConditionHandler(
                providerOf(PaymentState.unpaid()));

        assertThat(handler.matches(transition(), context(workflow(true)))).isFalse();
    }

    @Test
    void noProviderDefaultsToUnpaidSoTheGateStaysShut() {
        ObjectProvider<PaymentStateProvider> emptyProvider = new ObjectProvider<>() {
            @Override
            public PaymentStateProvider getObject() {
                throw new IllegalStateException("no bean");
            }

            @Override
            public PaymentStateProvider getIfAvailable() {
                return null;
            }

            @Override
            public PaymentStateProvider getObject(Object... args) {
                throw new IllegalStateException("no bean");
            }
        };
        var handler = new PaymentStateSatisfiedConditionHandler(emptyProvider);

        assertThat(handler.matches(transition(), context(workflow(true)))).isFalse();
    }
}
