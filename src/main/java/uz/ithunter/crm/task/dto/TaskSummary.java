package uz.ithunter.crm.task.dto;

import java.time.Instant;
import java.util.UUID;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.task.TaskStatus;

/** Lightweight projection used in {@code PageResponse<TaskSummary>} from GET /tasks. */
public record TaskSummary(
        UUID id,
        UUID caseId,
        UUID caseStageId,
        String title,
        UUID assignedDepartmentId,
        UUID assignedUserId,
        TaskStatus status,
        ProcessingMode processingMode,
        Instant deadline,
        boolean overdue,
        long version) {
}
