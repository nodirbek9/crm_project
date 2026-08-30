package uz.ithunter.crm.admin;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.admin.dto.RolePermissionsResponse;
import uz.ithunter.crm.admin.dto.UpdateRolePermissionsRequest;

@RestController
@RequestMapping("/api/admin/roles/{code}/permissions")
public class AdminRolePermissionController {

    private final AdminRolePermissionService adminRolePermissionService;

    public AdminRolePermissionController(AdminRolePermissionService adminRolePermissionService) {
        this.adminRolePermissionService = adminRolePermissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:VIEW')")
    public RolePermissionsResponse get(@PathVariable String code) {
        return adminRolePermissionService.get(code);
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:EDIT')")
    public RolePermissionsResponse update(@PathVariable String code,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return adminRolePermissionService.update(code, request);
    }
}
