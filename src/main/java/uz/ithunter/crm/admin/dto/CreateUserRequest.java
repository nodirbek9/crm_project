package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String fullName,
        UUID departmentId,
        UUID positionId,
        @NotEmpty Set<String> roleCodes) {
}
