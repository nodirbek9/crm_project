package uz.ithunter.crm.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uz.ithunter.crm.application.SubmissionChannel;

/**
 * API_SPEC.md 3. {@code applicantId} is required only when the caller is staff acting on someone
 * else's behalf (PAPER/SINGLE_WINDOW); an applicant creating their own application has it forced to
 * their own id by {@code ApplicationService} regardless of what's sent here.
 */
public record CreateApplicationRequest(
        @NotNull UUID serviceId,
        @NotNull SubmissionChannel submissionChannel,
        UUID applicantId,
        Map<String, Object> formData,
        List<Map<String, Object>> items) {
}
