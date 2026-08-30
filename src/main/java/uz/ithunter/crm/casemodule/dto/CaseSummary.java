package uz.ithunter.crm.casemodule.dto;

import java.time.Instant;
import java.util.UUID;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * One row of {@code GET /cases} (API_SPEC.md 4). Flat on purpose: a list of 50 cases should not carry
 * 50 nested department objects, and everything here is what a staff member scans a worklist by.
 *
 * <p>{@code currentStage*} is resolved from the ACTIVE {@code case_stage} rows, not from
 * {@code current_stage_id}, which is NULL while a parallel group is open (PLAN_REVIEW M1). When two
 * branches are open the first by {@code sequence} is shown and {@code parallelStages} says how many
 * there are - hiding that would make the list look wrong to the person whose case it is.
 */
public record CaseSummary(
        UUID id,
        String caseNumber,
        String applicationNumber,
        String applicantName,
        String serviceName,
        CaseStatus status,
        String currentStageCode,
        String currentStageName,
        int parallelStages,
        ProcessingMode processingMode,
        Instant dueAt,
        boolean overdue,
        Instant createdAt) {
}
