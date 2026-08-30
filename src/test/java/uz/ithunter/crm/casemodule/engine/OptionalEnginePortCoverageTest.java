package uz.ithunter.crm.casemodule.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.casemodule.engine.port.ApprovalStateProvider;
import uz.ithunter.crm.casemodule.engine.port.PaymentStateProvider;
import uz.ithunter.crm.casemodule.engine.port.StageWorkRecorder;
import uz.ithunter.crm.casemodule.port.PriceConfirmationChecker;
import uz.ithunter.crm.finance.port.OverduePaymentTaskCreator;

/**
 * External review finding: {@code TransitionEvaluator.afterPropertiesSet()} already guarantees
 * every {@code ConditionType} has a registered {@link uz.ithunter.crm.casemodule.engine.condition.ConditionHandler}
 * CLASS, but that check cannot see inside a handler that wraps an OPTIONAL port via
 * {@code ObjectProvider} (the deliberate "safe default while a later phase is unimplemented"
 * pattern of ASSUMPTIONS.md A25). {@code ApprovalStateProvider} went unimplemented for three
 * phases with the handler class present the whole time, silently returning {@code false} forever
 * - exactly the gap this test closes: assert the ACTUAL bean exists, not just that something
 * claims the slot.
 *
 * <p>Every port listed here MUST have a real implementation by the end of Phase 13 - if a future
 * phase reintroduces one of these as interface-only scaffolding, this test fails at context
 * startup rather than three phases later during a demo walkthrough.
 */
@SpringBootTest
class OptionalEnginePortCoverageTest extends AbstractIntegrationTest {

    @Autowired(required = false)
    private PaymentStateProvider paymentStateProvider;

    @Autowired(required = false)
    private ApprovalStateProvider approvalStateProvider;

    @Autowired(required = false)
    private StageWorkRecorder stageWorkRecorder;

    @Autowired(required = false)
    private PriceConfirmationChecker priceConfirmationChecker;

    @Autowired(required = false)
    private OverduePaymentTaskCreator overduePaymentTaskCreator;

    @Test
    void everyEngineSeamPortHasARealImplementationBean() {
        assertThat(paymentStateProvider).as("PAYMENT_STATE_SATISFIED's port").isNotNull();
        assertThat(approvalStateProvider).as("APPROVAL_ROUND_COMPLETED's port").isNotNull();
        assertThat(stageWorkRecorder).as("performed-work recording's port").isNotNull();
        assertThat(priceConfirmationChecker).as("item-lock price-confirmation port").isNotNull();
        assertThat(overduePaymentTaskCreator).as("overdue-payment scheduler's port").isNotNull();
    }
}
