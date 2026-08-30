package uz.ithunter.crm.approval.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class ApprovalTaskResponse {
    private UUID id;
    private UUID approvalRoundId;
    private String participantKind;
    private UUID participantUserId;
    private UUID participantDepartmentId;
    private boolean required;
    private int sequenceNo;
    private String status;
    private String comment;
    private UUID decidedById;
    private Instant decidedAt;
    private Instant dueAt;
    private Instant createdAt;
}
