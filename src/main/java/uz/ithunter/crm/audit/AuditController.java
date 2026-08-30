package uz.ithunter.crm.audit;

import java.time.Instant;
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
import uz.ithunter.crm.audit.dto.AuditIntegrityResponse;
import uz.ithunter.crm.audit.dto.AuditLogResponse;
import uz.ithunter.crm.auth.CustomUserPrincipal;

@RestController
@RequestMapping("/api")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** GET /audit — filtered audit log (spec 20.x, AUDIT:VIEW). */
    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('AUDIT:VIEW')")
    public Page<AuditLogResponse> search(
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return auditService.search(caseId, userId, action, entityType, from, to, pageable);
    }

    /** GET /cases/{id}/audit — case-scoped audit log. */
    @GetMapping("/cases/{id}/audit")
    @PreAuthorize("hasAuthority('AUDIT:VIEW')")
    public Page<AuditLogResponse> searchByCase(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Pageable pageable) {
        return auditService.searchByCase(id, principal, pageable);
    }

    /** GET /audit/integrity — hash-chain check. */
    @GetMapping("/audit/integrity")
    @PreAuthorize("hasAuthority('AUDIT:VIEW')")
    public AuditIntegrityResponse integrity() {
        return auditService.checkIntegrity();
    }
}
