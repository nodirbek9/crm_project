package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateExternalStageRequest(@NotBlank String code, @NotBlank String nameForApplicant, int sequence) {
}
