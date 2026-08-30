package uz.ithunter.crm.task.dto;

import java.time.Instant;
import java.util.UUID;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.task.TaskStatus;

/** Full task detail returned from GET /tasks/{id} and all mutation endpoints. */
public record TaskResponse(
        UUID id,
        UUID caseId,
        UUID caseStageId,
        UUID workflowStageId,
        String title,
        String description,
        UUID assignedDepartmentId,
        UUID assignedUserId,
        UUID assignedById,
        Instant assignedAt,
        TaskStatus status,
        ProcessingMode processingMode,
        Instant deadline,
        boolean overdue,
        Instant startedAt,
        Instant completedAt,
        int revisionCount,
        long version,
        Instant createdAt) {
}
