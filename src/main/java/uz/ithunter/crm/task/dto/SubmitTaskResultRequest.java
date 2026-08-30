package uz.ithunter.crm.task.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /tasks/{id}/results. */
public record SubmitTaskResultRequest(
        @NotBlank String payload,
        String summary,
        String revisionReason) {
}
