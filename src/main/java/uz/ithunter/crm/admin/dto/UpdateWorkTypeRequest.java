package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateWorkTypeRequest(
        @NotBlank String name, String serviceScope, String stageKind,
        boolean requiresContractAmountBracket, String basisDocumentDescription, boolean active) {
}
