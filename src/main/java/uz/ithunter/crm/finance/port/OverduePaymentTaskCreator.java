package uz.ithunter.crm.finance.port;

import java.util.UUID;

/**
 * The seam between {@link uz.ithunter.crm.finance.PaymentWaitingScheduler} and the execution block
 * (spec 12.9: an overdue payment "raises a manager decision task", never an auto-rejection).
 *
 * <p>{@code Task} does not exist until Phase 9, so Phase 8 ships this interface with no
 * implementation - the same seam pattern Phase 7 used for {@code ApprovalStateProvider}/
 * {@code PaymentStateProvider} (ASSUMPTIONS.md A25). The scheduler injects this as an
 * {@code ObjectProvider} and simply skips task creation when no bean is present; the overdue flag
 * and audit row are written regardless, so nothing observable is lost - only the follow-up task,
 * which Phase 9 supplies (ASSUMPTIONS.md A35).
 */
public interface OverduePaymentTaskCreator {

    void createDecisionTask(UUID caseId, UUID paymentId);
}
