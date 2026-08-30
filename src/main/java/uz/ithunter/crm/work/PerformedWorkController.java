package uz.ithunter.crm.work;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.work.dto.PerformedWorkResponse;

@RestController
@RequestMapping("/api")
public class PerformedWorkController {

    private final PerformedWorkService performedWorkService;

    public PerformedWorkController(PerformedWorkService performedWorkService) {
        this.performedWorkService = performedWorkService;
    }

    /**
     * GET /cases/{id}/performed-works — scoped to a single case (spec 8.1).
     *
     * <p>Gated on {@code PERFORMED_WORK:VIEW}, not {@code isAuthenticated()}: the applicant holds
     * no {@code PERFORMED_WORK:*} grant at all (spec 15.13, test S-07 - the applicant tracking view
     * must never surface performed-work data), and {@code CaseAccessPolicy.requireCanView} alone
     * would let them through to their own case, silently leaking this sub-resource.
     */
    @GetMapping("/cases/{id}/performed-works")
    @PreAuthorize("hasAuthority('PERFORMED_WORK:VIEW')")
    public List<PerformedWorkResponse> listByCaseId(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return performedWorkService.listByCaseId(id, principal);
    }

    /**
     * GET /performed-works — filtered listing with department-scoped authz (spec 8.1).
     *
     * <p>{@code hasAuthority(...)} checks a PERMISSION code (e.g. {@code PERFORMED_WORK:VIEW}),
     * never a role name - this codebase's {@code PermissionAuthorityResolver} deliberately never
     * puts role names into the authorities set (Phase 3), so an {@code or hasAuthority('ADMIN')}
     * clause could never match anything; removed rather than left as dead, misleading code. ADMIN
     * holds no {@code PERFORMED_WORK:*} grant in the seeded matrix anyway (spec 16.17).
     */
    @GetMapping("/performed-works")
    @PreAuthorize("hasAuthority('PERFORMED_WORK:VIEW')")
    public Page<PerformedWorkResponse> search(
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID executorId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String workTypeCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Pageable pageable) {
        return performedWorkService.search(caseId, executorId, departmentId, workTypeCode, from, to, principal, pageable);
    }
}
