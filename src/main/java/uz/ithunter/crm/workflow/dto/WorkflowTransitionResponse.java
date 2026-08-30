package uz.ithunter.crm.workflow.dto;

import java.util.UUID;

public record WorkflowTransitionResponse(
        UUID id, UUID fromStageId, UUID toStageId, String conditionType, String conditionValue, int sequence) {
}
