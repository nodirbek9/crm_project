package uz.ithunter.crm.application.dto;

import java.time.Instant;
import java.util.UUID;
import uz.ithunter.crm.application.SubmissionChannel;

/** Lighter list-view shape for {@code GET /applications} (API_SPEC.md 3). */
public record ApplicationSummary(
        UUID id, String number, UUID applicantId, UUID serviceId, SubmissionChannel submissionChannel,
        String status, Instant submittedAt, Instant registeredAt) {
}
