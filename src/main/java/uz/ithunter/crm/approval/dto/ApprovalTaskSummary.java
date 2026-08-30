package uz.ithunter.crm.approval.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class ApprovalTaskSummary {
    private UUID id;
    private UUID approvalRoundId;
    private UUID documentVersionId;
    private UUID caseId;
    private String status;
    private Instant createdAt;
}
