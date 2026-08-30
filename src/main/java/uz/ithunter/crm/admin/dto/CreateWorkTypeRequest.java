package uz.ithunter.crm.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkTypeRequest(
        @NotBlank String code, @NotBlank String name, String serviceScope, String stageKind,
        boolean requiresContractAmountBracket, String basisDocumentDescription) {
}
