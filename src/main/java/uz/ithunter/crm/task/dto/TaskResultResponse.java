package uz.ithunter.crm.task.dto;

import java.time.Instant;
import java.util.UUID;
import uz.ithunter.crm.task.TaskResultStatus;

/** One row in the version chain returned from GET /tasks/{id}/results or POST /tasks/{id}/results. */
public record TaskResultResponse(
        UUID id,
        UUID taskId,
        int versionNo,
        String payload,
        String summary,
        TaskResultStatus status,
        UUID authorId,
        Instant createdAt,
        UUID supersedesId,
        String revisionReason,
        UUID approvedById,
        Instant approvedAt) {
}
