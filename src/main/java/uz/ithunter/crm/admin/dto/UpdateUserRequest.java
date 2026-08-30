package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

/** {@code version} is echoed by the client per API_SPEC.md 0's optimistic-locking convention. */
public record UpdateUserRequest(
        @NotBlank String fullName,
        UUID departmentId,
        UUID positionId,
        @NotEmpty Set<String> roleCodes,
        long version) {
}
