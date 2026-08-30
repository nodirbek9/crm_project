package uz.ithunter.crm.approval;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.engine.port.ApprovalStateProvider;

/**
 * The Phase 10 implementation of the seam Phase 7 shipped as an interface only (see
 * {@link ApprovalStateProvider}'s javadoc, ASSUMPTIONS.md A25). Never actually written until
 * Phase 13's demo walkthrough proved the gap: {@code ENDORSEMENT -> SIGNING} never advanced
 * through the real {@code APPROVAL_ROUND_COMPLETED} condition because no bean answered it - every
 * prior test of that transition used a {@code ConditionType.ALWAYS} route instead, which never
 * exercised this gate at all.
 *
 * <p>{@code workflowStageId} is accepted (the port's contract, matched to how
 * {@code PAYMENT_STATE_SATISFIED}'s sibling gate reads) but not needed here: a case only ever has
 * one ENDORSEMENT stage open at a time, and {@link ApprovalRoundRepository}'s own javadoc explains
 * why "any COMPLETED_APPROVED round for this case" is correct across a reject/revise cycle without
 * having to correlate rounds to a specific stage row.
 */
@Component
public class CaseApprovalStateProvider implements ApprovalStateProvider {

    private final ApprovalRoundRepository approvalRoundRepository;

    public CaseApprovalStateProvider(ApprovalRoundRepository approvalRoundRepository) {
        this.approvalRoundRepository = approvalRoundRepository;
    }

    @Override
    public boolean isRoundCompleted(UUID caseId, UUID workflowStageId) {
        return approvalRoundRepository.existsByElectronicCaseIdAndStatus(
                caseId, ApprovalRoundStatus.COMPLETED_APPROVED);
    }
}
