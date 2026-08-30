package uz.ithunter.crm.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;

/**
 * Derives the audit actor from the current request instead of trusting a caller-supplied value -
 * the whole point of the {@link AuditWriter} port. Nothing in the service layer can therefore
 * attribute a change to somebody else.
 *
 * <p>{@code audit_log.actor_role_code} is a single {@code varchar(40)}, but a user may hold several
 * roles. This records the alphabetically first role code, which is deterministic and fits the
 * column; the full role set is always recoverable from {@code user_role} via {@code user_id}. See
 * ASSUMPTIONS.md A14.
 */
@Component
public class AuditActorResolver {

    private static final int IP_COLUMN_LENGTH = 45;

    private final UserRepository userRepository;

    public AuditActorResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AuditActor resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            return AuditActor.system();
        }

        // The principal carries no role codes on purpose (JWT claims are never trusted for
        // authorization, SECURITY_SPEC.md 1), so the row is re-read - normally a first-level-cache
        // hit inside the mutation's own transaction.
        User user = userRepository.findById(principal.userId()).orElse(null);
        String roleCode = user == null ? null : firstRoleCode(user);
        UUID departmentId = principal.departmentId() != null
                ? principal.departmentId()
                : (user == null ? null : user.getDepartmentId());

        return new AuditActor(principal.userId(), roleCode, departmentId, currentIpAddress());
    }

    private String firstRoleCode(User user) {
        return user.getRoles().stream()
                .map(Role::getCode)
                .map(Enum::name)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private String currentIpAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return ip.length() > IP_COLUMN_LENGTH ? ip.substring(0, IP_COLUMN_LENGTH) : ip;
    }
}
