package uz.ithunter.crm.workflow.dto;

import java.util.UUID;

public record WorkflowSummary(UUID id, UUID serviceId, String code, int version, String name, String status) {
}
