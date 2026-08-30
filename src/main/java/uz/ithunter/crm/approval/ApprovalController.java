package uz.ithunter.crm.approval;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.ithunter.crm.approval.dto.ApprovalDecisionRequest;
import uz.ithunter.crm.approval.dto.ApprovalRoundResponse;
import uz.ithunter.crm.approval.dto.ApprovalTaskResponse;
import uz.ithunter.crm.approval.dto.ApprovalTaskSummary;
import uz.ithunter.crm.approval.dto.StartApprovalRequest;
import uz.ithunter.crm.auth.CustomUserPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/documents/{id}/versions/{versionNo}/approval-rounds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPROVAL:ENDORSE')")
    public ApprovalRoundResponse startRound(@PathVariable UUID id,
                                            @PathVariable int versionNo,
                                            @Valid @RequestBody StartApprovalRequest request,
                                            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return approvalService.startRound(id, versionNo, request, principal);
    }

    @GetMapping("/approval-rounds/{id}")
    @PreAuthorize("isAuthenticated()") // Policy checked in service
    public ApprovalRoundResponse getRound(@PathVariable UUID id,
                                          @AuthenticationPrincipal CustomUserPrincipal principal) {
        return approvalService.getRound(id, principal);
    }

    @GetMapping("/approvals/my")
    @PreAuthorize("isAuthenticated()")
    public Page<ApprovalTaskSummary> listMyApprovals(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                     Pageable pageable) {
        return approvalService.listMyApprovals(principal, pageable);
    }

    @PostMapping("/approval-tasks/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApprovalTaskResponse approve(@PathVariable UUID id,
                                        @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
                                        @AuthenticationPrincipal CustomUserPrincipal principal) {
        if (request == null) {
            request = new ApprovalDecisionRequest();
        }
        return approvalService.approve(id, request, principal);
    }

    @PostMapping("/approval-tasks/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApprovalTaskResponse reject(@PathVariable UUID id,
                                       @Valid @RequestBody ApprovalDecisionRequest request,
                                       @AuthenticationPrincipal CustomUserPrincipal principal) {
        return approvalService.reject(id, request, principal);
    }
}
