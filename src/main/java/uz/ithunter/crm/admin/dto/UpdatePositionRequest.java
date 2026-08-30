package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePositionRequest(@NotBlank String name, boolean active) {
}
