package uz.ithunter.crm.casemodule.engine.condition;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.engine.port.PaymentState;
import uz.ithunter.crm.casemodule.engine.port.PaymentStateProvider;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.WorkflowTransition;

/**
 * WORKFLOW_ENGINE_DESIGN.md 10, verbatim (spec 12.8):
 * {@code payment.status == PAID || (workflow.allowExecutionBeforeFullPayment && status == PARTIALLY_PAID)}.
 *
 * <p>Note where the permission lives: {@code allow_execution_before_full_payment} is a column on the
 * <b>route</b>, not a constant and not a runtime argument. One service may start work on a deposit and
 * another may not, and that difference is configuration (tests U-11 and U-12, which Phase 8 owns).
 *
 * <p>{@link PaymentStateProvider} has no implementation in Phase 7 - {@code Payment} is Phase 8's.
 * With no provider present the state is {@link PaymentState#unpaid()}, so the gate stays shut. A
 * payment gate that opens because the finance module is missing would be the worst possible default.
 */
@Component
public class PaymentStateSatisfiedConditionHandler implements ConditionHandler {

    private final ObjectProvider<PaymentStateProvider> paymentStateProvider;

    public PaymentStateSatisfiedConditionHandler(ObjectProvider<PaymentStateProvider> paymentStateProvider) {
        this.paymentStateProvider = paymentStateProvider;
    }

    @Override
    public ConditionType supportedType() {
        return ConditionType.PAYMENT_STATE_SATISFIED;
    }

    @Override
    public boolean matches(WorkflowTransition transition, TransitionContext context) {
        PaymentStateProvider provider = paymentStateProvider.getIfAvailable();
        PaymentState state = provider == null
                ? PaymentState.unpaid()
                : provider.forCase(context.electronicCase().getId());
        if (state == null) {
            return false;
        }
        return state.fullyPaid()
                || (context.workflow().isAllowExecutionBeforeFullPayment() && state.partiallyPaid());
    }
}
