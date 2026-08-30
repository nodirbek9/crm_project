package uz.ithunter.crm.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import uz.ithunter.crm.application.SubmissionChannel;

public record ApplicationResponse(
        UUID id, String number, UUID applicantId, UUID serviceId, SubmissionChannel submissionChannel,
        UUID registeredById, Instant submittedAt, Instant registeredAt, String status,
        Map<String, Object> formData, long version, Instant createdAt, Instant updatedAt) {
}
