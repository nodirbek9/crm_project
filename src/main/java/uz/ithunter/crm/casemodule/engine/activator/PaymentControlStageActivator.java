package uz.ithunter.crm.casemodule.engine.activator;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.workflow.StageType;

/**
 * Starts the payment waiting clock (WORKFLOW_ENGINE_DESIGN.md 7, spec 12.9, ASSUMPTIONS.md A5/A10).
 *
 * <p>The waiting period comes from the route's {@code payment_waiting_days} (10 by default), so the
 * value is configuration rather than a constant, and it is read from the case's PINNED workflow
 * version. The deadline is mirrored onto {@code electronic_case.payment_due_at} so that "which cases
 * are waiting for money" is one indexed read on the case table instead of a join.
 *
 * <p>What this activator deliberately does not do: create the {@code Payment} row, and never reject
 * the case when the clock runs out. {@code Payment} is Phase 8's entity, and spec 12.9 is explicit
 * that an overdue payment raises a manager decision - the system does not cancel on its own.
 *
 * <p>{@code payment_due_at} is re-stamped on every activation rather than only on the first. A case
 * that comes back to payment control after a price change is genuinely waiting again from that
 * moment, and keeping the original date would report it overdue for a debt it did not yet owe.
 */
@Component
public class PaymentControlStageActivator implements StageActivator {

    @Override
    public StageType supportedType() {
        return StageType.PAYMENT_CONTROL;
    }

    @Override
    public void onActivate(StageActivationContext context) {
        int waitingDays = Math.max(1, context.workflow().getPaymentWaitingDays());
        Instant dueAt = Instant.now().plus(Duration.ofDays(waitingDays));
        context.electronicCase().setPaymentDueAt(dueAt);
        context.electronicCase().setPaymentOverdue(false);
        if (context.stage().getDueAt() == null) {
            context.stage().setDueAt(dueAt);
        }
    }
}
