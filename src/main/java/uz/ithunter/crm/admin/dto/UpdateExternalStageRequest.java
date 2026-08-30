package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateExternalStageRequest(@NotBlank String nameForApplicant, int sequence, boolean active) {
}
