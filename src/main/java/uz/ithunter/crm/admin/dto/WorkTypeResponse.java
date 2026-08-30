package uz.ithunter.crm.admin.dto;

import java.util.UUID;

public record WorkTypeResponse(
        UUID id, String code, String name, String serviceScope, String stageKind,
        boolean requiresContractAmountBracket, String basisDocumentDescription, boolean active) {
}
