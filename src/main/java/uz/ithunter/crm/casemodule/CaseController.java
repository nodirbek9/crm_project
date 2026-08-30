package uz.ithunter.crm.casemodule;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.dto.ApplicantTrackingResponse;
import uz.ithunter.crm.casemodule.dto.CaseItemRequest;
import uz.ithunter.crm.casemodule.dto.CaseItemResponse;
import uz.ithunter.crm.casemodule.dto.CaseResponse;
import uz.ithunter.crm.casemodule.dto.CaseSummary;
import uz.ithunter.crm.casemodule.dto.CommentResponse;
import uz.ithunter.crm.casemodule.dto.CreateCommentRequest;
import uz.ithunter.crm.casemodule.dto.PrimaryCheckRequest;
import uz.ithunter.crm.casemodule.dto.StageTimelineItem;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.shared.dto.PageResponse;

/**
 * API_SPEC.md 4 — Cases. Every endpoint carries layer-1 authorization via {@code @PreAuthorize};
 * layer-2 (object-level) is enforced inside {@link CaseService} via {@link CaseAccessPolicy}.
 */
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CASE:VIEW')")
    public PageResponse<CaseSummary> list(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) ProcessingMode mode,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String stageCode,
            @RequestParam(required = false) String q,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.list(status, serviceId, departmentId, mode, overdue, stageCode, q,
                pageable, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CASE:VIEW')")
    public CaseResponse get(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.get(id, principal);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAuthority('CASE:VIEW')")
    public List<StageTimelineItem> timeline(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.timeline(id, principal);
    }

    @GetMapping("/{id}/tracking")
    @PreAuthorize("hasAuthority('CASE:VIEW')")
    public ApplicantTrackingResponse tracking(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.tracking(id, principal);
    }

    @PostMapping("/{id}/primary-check")
    @PreAuthorize("hasAuthority('PRIMARY_CHECK:CREATE')")
    public CaseResponse primaryCheck(@PathVariable UUID id,
            @Valid @RequestBody PrimaryCheckRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.performPrimaryCheck(id, request, principal);
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('CASE:VIEW')")
    public List<CaseItemResponse> listItems(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.listItems(id, principal);
    }

    @PutMapping("/{id}/items")
    @PreAuthorize("hasAuthority('CASE:EDIT')")
    public List<CaseItemResponse> updateItems(@PathVariable UUID id,
            @Valid @RequestBody List<CaseItemRequest> items,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.updateItems(id, items, principal);
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('CASE:VIEW')")
    public ResponseEntity<CommentResponse> addComment(@PathVariable UUID id,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        CommentResponse response = caseService.addComment(id, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('CASE:VIEW')")
    public List<CommentResponse> listComments(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return caseService.listComments(id, principal);
    }
}
