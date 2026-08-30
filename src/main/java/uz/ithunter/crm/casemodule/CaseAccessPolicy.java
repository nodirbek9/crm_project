package uz.ithunter.crm.casemodule;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.task.TaskRepository;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;

/**
 * The object-level authorization (layer 2 of SECURITY_SPEC.md 4).
 *
 * <p>Phase 7 left this deliberately coarse for {@code DEPARTMENT_HEAD} and {@code SPECIALIST}:
 * until Phase 9 created {@code Task}, "my department touches the case" was the only checkable proxy
 * for "I am assigned to it" (ASSUMPTIONS.md A26). Phase 9 adds the task-existence clauses
 * SECURITY_SPEC.md 5 specifies - but keeps the department-touch fallback for BOTH roles rather than
 * dropping it for SPECIALIST as the spec's bare pseudocode would read, because that bare form
 * blocks a specialist from the primary-check step itself (no task exists at that point in a case's
 * life - see ASSUMPTIONS.md A39).
 */
@Component
@RequiredArgsConstructor
public class CaseAccessPolicy {

    private final UserRepository userRepository;
    private final ObjectProvider<TaskRepository> taskRepository;

    /** SECURITY_SPEC.md 5's {@code canViewCase}, transcribed role by role. */
    public boolean canViewCase(CustomUserPrincipal principal, ElectronicCase electronicCase) {
        if (principal == null || electronicCase == null) {
            return false;
        }
        for (RoleCode role : rolesOf(principal)) {
            if (grantsView(role, principal, electronicCase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The enforcing variant. Note the two different failures, which are not interchangeable
     * (SECURITY_SPEC.md 6):
     *
     * <ul>
     *   <li>an applicant reaching for someone else's case gets <b>404</b> - a 403 would confirm that
     *       the case exists, which is itself a leak about another applicant;
     *   <li>a staff member who may see cases but not this one gets <b>403</b> - inside the
     *       organisation the case's existence is not a secret, and a 404 there would send a
     *       specialist hunting for a bug that isn't one.
     * </ul>
     */
    public void requireCanView(CustomUserPrincipal principal, ElectronicCase electronicCase) {
        if (canViewCase(principal, electronicCase)) {
            return;
        }
        if (isApplicant(principal)) {
            throw new NotFoundException("Case not found");
        }
        throw new AccessDeniedDomainException("PERMISSION_DENIED", "You may not view this case");
    }

    /**
     * Guards the endpoints API_SPEC.md 4 marks "staff only - never the applicant": internal comments
     * (spec 13.5.1, 17.8) and the internal timeline. An applicant holding {@code CASE:VIEW} passes
     * the {@code @PreAuthorize} layer and passes {@code canViewCase} on their own case, so the
     * applicant/staff distinction has to be made here or not at all.
     */
    public void requireStaff(CustomUserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED", "Authentication is required");
        }
        if (isApplicant(principal)) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED", "This view is internal to staff");
        }
    }

    /**
     * True when the caller acts as an applicant. Checked by role rather than by
     * {@code principal.applicantId() != null} alone: the {@code applicant_id IS NOT NULL <=> role
     * APPLICANT} invariant is DB-enforced, but authorization should not lean on an invariant it can
     * verify directly.
     */
    public boolean isApplicant(CustomUserPrincipal principal) {
        return principal != null && rolesOf(principal).contains(RoleCode.APPLICANT);
    }

    /**
     * The {@code applicantId} that applicant-facing repository queries bind as a parameter
     * (SECURITY_SPEC.md 5, "repository-level defence in depth"), or {@code null} for staff, which
     * those queries read as "no applicant restriction".
     */
    public UUID applicantScopeOf(CustomUserPrincipal principal) {
        return isApplicant(principal) ? principal.applicantId() : null;
    }

    /**
     * The department filter {@code GET /cases} must apply for this caller, so that the list shows
     * exactly the cases {@link #canViewCase} would open. Without it a SPECIALIST would receive a page
     * of case numbers, applicant names and services for cases that {@code GET /cases/{id}} then
     * answers with 403 - the list itself would be the leak.
     *
     * <p>{@code null} means "no department restriction": ACCOUNTANT, HEAD_OF_CERTIFICATION_BODY and
     * OPERATOR read across the organisation (spec 3.2, 3.6, 17.1), and an APPLICANT is not restricted
     * by department at all but by {@code applicantId}, which
     * {@link ElectronicCaseRepository#search} binds separately.
     *
     * <p>Throws rather than returning a sentinel in the two cases where no honest filter exists: a
     * caller whose roles never grant case access (ADMIN alone - spec 16.17), and a department-scoped
     * role with no {@code department_id} on the account, which would otherwise silently widen to the
     * whole system.
     */
    public UUID departmentScopeOf(CustomUserPrincipal principal) {
        Set<RoleCode> roles = rolesOf(principal);
        if (principal == null || roles.isEmpty()) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED", "You may not browse cases");
        }
        if (roles.contains(RoleCode.APPLICANT) || roles.contains(RoleCode.ACCOUNTANT)
                || roles.contains(RoleCode.HEAD_OF_CERTIFICATION_BODY) || roles.contains(RoleCode.OPERATOR)) {
            return null;
        }
        if (!roles.contains(RoleCode.DEPARTMENT_HEAD) && !roles.contains(RoleCode.SPECIALIST)) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED", "Your role does not open case content");
        }
        if (principal.departmentId() == null) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED",
                    "Your account is not attached to a department");
        }
        return principal.departmentId();
    }

    public Set<RoleCode> rolesOf(CustomUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            return EnumSet.noneOf(RoleCode.class);
        }
        User user = userRepository.findById(principal.userId()).orElse(null);
        if (user == null) {
            // A token signed for a user that has since been deleted: authenticate yes, authorize no.
            return EnumSet.noneOf(RoleCode.class);
        }
        Set<RoleCode> codes = EnumSet.noneOf(RoleCode.class);
        user.getRoles().forEach(role -> codes.add(role.getCode()));
        return codes;
    }

    /**
     * One role, one verdict. Multiple roles are combined as a union (any granting role wins), so
     * ADMIN's {@code false} does not veto another role a user may also hold - it simply contributes
     * nothing. ADMIN alone therefore cannot open a case: spec 16.17 keeps the administrator on
     * configuration and out of the case content, and test S-05 pins it.
     */
    private boolean grantsView(RoleCode role, CustomUserPrincipal principal, ElectronicCase electronicCase) {
        return switch (role) {
            case APPLICANT -> principal.applicantId() != null
                    && principal.applicantId().equals(electronicCase.getApplicantId());
            // Read-only projections, not editing rights: ACCOUNTANT sees the finance side (spec 3.6),
            // HEAD_OF_CERTIFICATION_BODY oversees every case (spec 3.2), OPERATOR monitors (spec 17.1).
            case ACCOUNTANT, HEAD_OF_CERTIFICATION_BODY, OPERATOR -> true;
            case DEPARTMENT_HEAD -> departmentTouchesCase(principal.departmentId(), electronicCase)
                    || taskRepository.getObject().existsByCaseIdAndAssignedDepartmentId(electronicCase.getId(), principal.departmentId());
            // SECURITY_SPEC.md 5's pseudocode for SPECIALIST is task-existence only, with no
            // department-touch fallback at all - taken completely literally, that breaks the
            // primary-check step itself (spec 3.4, 4.5: PRIMARY_CHECK is a SPECIALIST action), since
            // no Task exists yet at that point in a case's life - registration activates
            // PRIMARY_CHECK, which creates no task (A37), and EXECUTION/FINAL_REVIEW tasks don't
            // exist until later. ASSUMPTIONS.md A39 keeps the department-touch fallback ALONGSIDE
            // the new task-based clauses (OR, not replacement) rather than dropping it - wider than
            // the pseudocode's bare form, but the pseudocode's own bare form regressed a working
            // Phase 7 flow (confirmed by running CaseLifecycleIntegrationTest, which the tightening
            // itself was never verified against before this fix).
            case SPECIALIST -> departmentTouchesCase(principal.departmentId(), electronicCase)
                    || taskRepository.getObject().existsByCaseIdAndAssignedUserId(electronicCase.getId(), principal.userId())
                    || taskRepository.getObject().existsByCaseIdAndAssignedDepartmentId(electronicCase.getId(), principal.departmentId());
            case ADMIN -> false;
        };
    }

    /**
     * Both the main responsible department and the participating set count (spec 4.13). A department
     * that owns only one branch of a parallel group still needs the case visible, otherwise the
     * laboratory cannot open the case it is being asked to test.
     */
    private boolean departmentTouchesCase(UUID departmentId, ElectronicCase electronicCase) {
        if (departmentId == null) {
            return false;
        }
        return departmentId.equals(electronicCase.getMainResponsibleDepartmentId())
                || electronicCase.getParticipatingDepartmentIds().contains(departmentId);
    }
}
