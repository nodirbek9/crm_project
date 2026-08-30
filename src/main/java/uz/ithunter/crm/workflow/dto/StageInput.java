package uz.ithunter.crm.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * References {@code externalStageCode}/{@code workTypeCode} by code, not internal id - an admin
 * author never needs to know a server-generated UUID (same addressing style the reference demo
 * seed uses).
 */
public record StageInput(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull String stageType,
        int sequence,
        String parallelGroup,
        boolean required,
        @NotBlank String externalStageCode,
        @NotBlank String internalStatusLabel,
        String responsibleRoleCode,
        UUID responsibleDepartmentId,
        @NotNull String assignmentMode,
        Integer deadlineDays,
        Integer expeditedDeadlineDays,
        String workTypeCode,
        String producesDocumentType,
        boolean requiresResult,
        boolean revisionAllowed,
        boolean approvalRequired,
        String approvalMode) {
}
