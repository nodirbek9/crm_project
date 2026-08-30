package uz.ithunter.crm.admin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.admin.dto.CreateUserRequest;
import uz.ithunter.crm.admin.dto.UpdateUserRequest;
import uz.ithunter.crm.admin.dto.UserResponse;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.ConflictException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * FINAL_IMPLEMENTATION_ORDER.md Phase 4: admin CRUD for users, {@code AuditWriter} wired into
 * every mutation. {@code APPLICANT} is rejected as a role here (see {@link #resolveRoles}) - that
 * role only ever attaches through Phase 5's self-registration flow, the only path that also sets
 * {@code applicantId}, which the {@code app_user} invariant requires alongside it.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditWriter auditWriter;

    public AdminUserService(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, AuditWriter auditWriter) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "A user with this email already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setDepartmentId(request.departmentId());
        user.setPositionId(request.positionId());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(resolveRoles(request.roleCodes()));
        user = userRepository.save(user);

        auditWriter.write(AuditEvent.administrative(AuditAction.USER_CREATED, "User", user.getId(),
                null, snapshot(user)));
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        if (user.getVersion() != request.version()) {
            throw new ObjectOptimisticLockingFailureException(User.class, id);
        }

        Map<String, Object> oldValue = snapshot(user);
        user.setFullName(request.fullName());
        user.setDepartmentId(request.departmentId());
        user.setPositionId(request.positionId());
        user.setRoles(resolveRoles(request.roleCodes()));
        user = userRepository.save(user);

        auditWriter.write(AuditEvent.administrative(AuditAction.USER_UPDATED, "User", user.getId(),
                oldValue, snapshot(user)));
        return toResponse(user);
    }

    @Transactional
    public UserResponse block(UUID id) {
        User user = findOrThrow(id);
        Map<String, Object> oldValue = Map.of("status", user.getStatus().name());
        user.setStatus(UserStatus.BLOCKED);
        user = userRepository.save(user);

        auditWriter.write(AuditEvent.administrative(AuditAction.USER_BLOCKED, "User", user.getId(),
                oldValue, Map.of("status", user.getStatus().name())));
        return toResponse(user);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Set<Role> resolveRoles(Set<String> roleCodes) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String code : roleCodes) {
            RoleCode roleCode;
            try {
                roleCode = RoleCode.valueOf(code);
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("VALIDATION_FAILED", "Unknown role code: " + code);
            }
            if (roleCode == RoleCode.APPLICANT) {
                throw new ValidationException("VALIDATION_FAILED",
                        "APPLICANT cannot be assigned through admin user management - it is set only "
                                + "by applicant self-registration, which also sets applicantId");
            }
            roles.add(roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Seeded role missing: " + roleCode)));
        }
        return roles;
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("departmentId", user.getDepartmentId());
        map.put("positionId", user.getPositionId());
        map.put("roles", user.getRoles().stream().map(Role::getCode).map(Enum::name).sorted().toList());
        map.put("status", user.getStatus().name());
        return map;
    }

    UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getDepartmentId(), user.getPositionId(),
                user.getRoles().stream().map(Role::getCode).map(Enum::name).sorted().toList(),
                user.getStatus().name(), user.getVersion(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
