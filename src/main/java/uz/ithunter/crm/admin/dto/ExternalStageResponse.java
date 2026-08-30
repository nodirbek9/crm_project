package uz.ithunter.crm.admin.dto;

import java.util.UUID;

public record ExternalStageResponse(UUID id, String code, String nameForApplicant, int sequence, boolean active) {
}
