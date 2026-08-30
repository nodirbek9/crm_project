package uz.ithunter.crm.admin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.admin.dto.RolePermissionsResponse;
import uz.ithunter.crm.admin.dto.UpdateRolePermissionsRequest;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.user.Permission;
import uz.ithunter.crm.user.PermissionRepository;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RolePermission;
import uz.ithunter.crm.user.RolePermissionRepository;
import uz.ithunter.crm.user.RoleRepository;

/**
 * FINAL_IMPLEMENTATION_ORDER.md Phase 4 / TEST_MATRIX.md A-04: this is the one place the seeded
 * grant matrix can change after Flyway. Every insert/delete raises {@code ROLE_PERMISSION_CHANGED}
 * (FINAL_DOMAIN_MODEL.md 1.3, spec 16.10) as a single administrative audit event - {@code case_id}
 * stays NULL, old/new values carry the full before/after permission-code sets.
 */
@Service
public class AdminRolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditWriter auditWriter;

    public AdminRolePermissionService(RoleRepository roleRepository, PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository, AuditWriter auditWriter) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public RolePermissionsResponse get(String roleCode) {
        Role role = resolveRole(roleCode);
        return new RolePermissionsResponse(role.getCode().name(), currentCodes(role));
    }

    @Transactional
    public RolePermissionsResponse update(String roleCode, UpdateRolePermissionsRequest request) {
        Role role = resolveRole(roleCode);
        List<RolePermission> existing = rolePermissionRepository.findByRole(role);
        Set<String> currentCodes = existing.stream().map(rp -> rp.getPermission().getCode())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Set<String> desiredCodes = request.permissionCodes();

        Map<String, Permission> byCode = new HashMap<>();
        for (String code : desiredCodes) {
            byCode.put(code, permissionRepository.findByCode(code)
                    .orElseThrow(() -> new ValidationException("VALIDATION_FAILED", "Unknown permission code: " + code)));
        }

        Set<String> toAdd = new HashSet<>(desiredCodes);
        toAdd.removeAll(currentCodes);
        Set<String> toRemove = new HashSet<>(currentCodes);
        toRemove.removeAll(desiredCodes);

        if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
            for (RolePermission rp : existing) {
                if (toRemove.contains(rp.getPermission().getCode())) {
                    rolePermissionRepository.delete(rp);
                }
            }
            for (String code : toAdd) {
                RolePermission rp = new RolePermission();
                rp.setRole(role);
                rp.setPermission(byCode.get(code));
                rolePermissionRepository.save(rp);
            }

            auditWriter.write(AuditEvent.administrative(AuditAction.ROLE_PERMISSION_CHANGED, "Role", role.getId(),
                    Map.of("permissionCodes", currentCodes.stream().sorted().toList()),
                    Map.of("permissionCodes", desiredCodes.stream().sorted().toList())));
        }

        return new RolePermissionsResponse(role.getCode().name(), desiredCodes);
    }

    private Set<String> currentCodes(Role role) {
        return rolePermissionRepository.findByRole(role).stream()
                .map(rp -> rp.getPermission().getCode())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private Role resolveRole(String roleCode) {
        RoleCode code;
        try {
            code = RoleCode.valueOf(roleCode);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("VALIDATION_FAILED", "Unknown role code: " + roleCode);
        }
        return roleRepository.findByCode(code).orElseThrow(() -> new NotFoundException("Role not found"));
    }
}
