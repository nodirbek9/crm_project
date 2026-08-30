package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateDepartmentRequest(@NotBlank String code, @NotBlank String name, UUID parentId, UUID headUserId) {
}
