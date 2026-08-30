package uz.ithunter.crm.task.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body for POST /tasks/{id}/assign and POST /tasks/{id}/reassign. */
public record AssignTaskRequest(
        @NotNull UUID userId,
        String reason) {
}
