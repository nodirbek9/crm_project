package uz.ithunter.crm.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code fromStageCode == null} marks the entry transition. */
public record TransitionInput(
        String fromStageCode,
        @NotBlank String toStageCode,
        @NotNull String conditionType,
        String conditionValue,
        int sequence) {
}
