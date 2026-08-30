package uz.ithunter.crm.casemodule.dto;

import java.time.Instant;
import java.util.UUID;
import uz.ithunter.crm.casemodule.CommentVisibility;

/** An internal comment as returned to staff (API_SPEC.md 4). Never reachable by an applicant. */
public record CommentResponse(
        UUID id,
        UUID caseId,
        UUID documentVersionId,
        UUID authorId,
        String authorName,
        UUID authorDepartmentId,
        CommentVisibility visibility,
        String body,
        Instant createdAt) {
}
