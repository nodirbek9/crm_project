package uz.ithunter.crm.casemodule.dto;

import java.time.Instant;
import java.util.UUID;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.workflow.StageType;

/**
 * One line of {@code GET /cases/{id}/timeline} - the INTERNAL history of the case's route
 * (API_SPEC.md 4, spec 4.11). Staff only: it exposes internal stage names, deadlines and how often a
 * stage was re-activated, which is exactly what spec 15.13 keeps away from the applicant.
 *
 * <p>{@code activationCount} is in here on purpose. A stage that was activated three times means the
 * work came back twice, and that is the single most useful number on this screen when someone asks
 * why a case is late.
 */
public record StageTimelineItem(
        UUID id,
        String stageCode,
        String stageName,
        StageType stageType,
        String internalStatusLabel,
        int sequence,
        String parallelGroup,
        boolean required,
        CaseStageStatus status,
        Instant activatedAt,
        Instant completedAt,
        Instant dueAt,
        boolean overdue,
        int activationCount) {
}
