package uz.ithunter.crm.task.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /tasks/{id}/return. reason is mandatory (400 REVISION_REASON_REQUIRED without it). */
public record ReturnTaskRequest(@NotBlank String reason) {
}
