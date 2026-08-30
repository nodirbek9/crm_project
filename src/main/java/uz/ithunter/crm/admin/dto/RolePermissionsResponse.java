package uz.ithunter.crm.admin.dto;

import java.util.Set;

public record RolePermissionsResponse(String roleCode, Set<String> permissionCodes) {
}
