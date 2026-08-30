package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePositionRequest(@NotBlank String code, @NotBlank String name) {
}
