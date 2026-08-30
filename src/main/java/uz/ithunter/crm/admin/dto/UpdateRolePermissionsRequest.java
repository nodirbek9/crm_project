package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** {@code permissionCodes} is the desired COMPLETE grant set for the role, not a delta. */
public record UpdateRolePermissionsRequest(@NotNull Set<String> permissionCodes) {
}
