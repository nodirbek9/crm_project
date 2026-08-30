package uz.ithunter.crm.workflow;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.workflow.dto.UpdateWorkflowStagesRequest;
import uz.ithunter.crm.workflow.dto.WorkflowResponse;
import uz.ithunter.crm.workflow.dto.WorkflowSummary;

/** API_SPEC.md 8. */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowDefinitionService workflowDefinitionService;

    public WorkflowController(WorkflowDefinitionService workflowDefinitionService) {
        this.workflowDefinitionService = workflowDefinitionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WORKFLOW_CONFIG:VIEW')")
    public List<WorkflowSummary> list() {
        return workflowDefinitionService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WORKFLOW_CONFIG:VIEW')")
    public WorkflowResponse get(@PathVariable UUID id) {
        return workflowDefinitionService.get(id);
    }

    @PostMapping("/{code}/versions")
    @PreAuthorize("hasAuthority('WORKFLOW_CONFIG:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse createDraftVersion(@PathVariable String code) {
        return workflowDefinitionService.createDraftVersion(code);
    }

    @PatchMapping("/{id}/stages")
    @PreAuthorize("hasAuthority('WORKFLOW_CONFIG:EDIT')")
    public WorkflowResponse updateStages(@PathVariable UUID id, @Valid @RequestBody UpdateWorkflowStagesRequest request) {
        return workflowDefinitionService.updateStages(id, request);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('WORKFLOW_CONFIG:EDIT')")
    public WorkflowResponse publish(@PathVariable UUID id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return workflowDefinitionService.publish(id, principal);
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('WORKFLOW_CONFIG:EDIT')")
    public WorkflowResponse retire(@PathVariable UUID id) {
        return workflowDefinitionService.retire(id);
    }
}
