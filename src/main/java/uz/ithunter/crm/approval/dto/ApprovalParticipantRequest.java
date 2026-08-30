package uz.ithunter.crm.approval.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.ithunter.crm.approval.ParticipantKind;
import java.util.UUID;

@Data
public class ApprovalParticipantRequest {
    @NotNull
    private ParticipantKind kind;
    private UUID userId;
    private UUID departmentId;
    private boolean required = true;
    private int sequenceNo = 0;
}
