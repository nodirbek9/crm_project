package uz.ithunter.crm.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Full replace of a DRAFT's stage/transition graph (API_SPEC.md 8's {@code PATCH /workflows/{id}/stages}). */
public record UpdateWorkflowStagesRequest(
        @NotEmpty @Valid List<StageInput> stages,
        @NotEmpty @Valid List<TransitionInput> transitions) {
}
