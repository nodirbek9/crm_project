package uz.ithunter.crm.casemodule.engine.port;

import java.util.UUID;

/**
 * The seam between the engine's {@code APPROVAL_ROUND_COMPLETED} condition and the approval block
 * (spec 13.7). {@code ApprovalRound} arrives in Phase 10, so Phase 7 ships the interface only; with
 * no implementation present the condition evaluates to {@code false} and the route waits, which is
 * the safe direction for a gate (ASSUMPTIONS.md A25).
 */
public interface ApprovalStateProvider {

    boolean isRoundCompleted(UUID caseId, UUID workflowStageId);
}
