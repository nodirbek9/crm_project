package uz.ithunter.crm.approval.dto;

import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ApprovalRoundResponse {
    private UUID id;
    private UUID documentVersionId;
    private UUID caseId;
    private String mode;
    private int roundNo;
    private String status;
    private UUID initiatedById;
    private Instant initiatedAt;
    private Instant completedAt;
    private List<ApprovalTaskResponse> tasks;
}
