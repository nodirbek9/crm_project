package uz.ithunter.crm.auth;

import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.user.RolePermissionRepository;
import uz.ithunter.crm.user.User;

/**
 * Resolves a user's effective permissions from {@code role_permission} on every call -
 * deliberately not cached in the JWT, so revoking a grant takes effect on the very next request
 * (SECURITY_SPEC.md 1). Used both by {@link JwtAuthenticationFilter} (to build the
 * {@code GrantedAuthority}s that back {@code @PreAuthorize}) and by {@code /auth/me}.
 */
@Service
public class PermissionAuthorityResolver {

    private final RolePermissionRepository rolePermissionRepository;

    public PermissionAuthorityResolver(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> resolvePermissionCodes(User user) {
        if (user.getRoles().isEmpty()) {
            return Set.of();
        }
        return rolePermissionRepository.findPermissionCodesByRoleIn(user.getRoles());
    }

    public List<GrantedAuthority> resolveAuthorities(User user) {
        return resolvePermissionCodes(user).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
