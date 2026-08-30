package uz.ithunter.crm.workflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        UUID id, UUID serviceId, String code, int version, String name, String description, String status,
        UUID mainResponsibleDepartmentId, boolean expeditedAllowed, boolean contractRequired,
        boolean paymentRequired, boolean allowExecutionBeforeFullPayment, int paymentWaitingDays,
        Integer totalDeadlineDays, boolean approvalRequired, Instant publishedAt, UUID publishedBy,
        List<WorkflowStageResponse> stages, List<WorkflowTransitionResponse> transitions) {
}
