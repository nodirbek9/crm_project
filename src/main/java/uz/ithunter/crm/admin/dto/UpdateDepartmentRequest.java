package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record UpdateDepartmentRequest(@NotBlank String name, UUID parentId, UUID headUserId, boolean active) {
}
