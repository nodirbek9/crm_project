package uz.ithunter.crm.workflow.dto;

import java.util.UUID;

public record WorkflowStageResponse(
        UUID id, String code, String name, String stageType, int sequence, String parallelGroup,
        boolean required, UUID externalStageId, String internalStatusLabel, String responsibleRoleCode,
        UUID responsibleDepartmentId, String assignmentMode, Integer deadlineDays, Integer expeditedDeadlineDays,
        UUID workTypeId, String producesDocumentType, boolean requiresResult, boolean revisionAllowed,
        boolean approvalRequired, String approvalMode) {
}
