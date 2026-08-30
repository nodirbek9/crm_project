package uz.ithunter.crm.work.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class PerformedWorkResponse {
    private UUID id;
    private UUID caseId;
    private UUID workTypeId;
    private UUID caseStageId;
    private UUID workflowStageId;
    private UUID serviceId;
    private UUID departmentId;
    private UUID executorUserId;
    private String processingMode;
    private Instant performedAt;
    private Instant recordedAt;
    private UUID recordedById;
    private UUID supportingDocumentVersionId;
    private String invoiceReference;
    private String contractAmountBracket;
    private boolean countable;
}
