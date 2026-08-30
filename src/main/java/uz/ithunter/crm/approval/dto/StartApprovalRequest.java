package uz.ithunter.crm.approval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import uz.ithunter.crm.workflow.ApprovalMode;
import java.util.List;

@Data
public class StartApprovalRequest {
    @NotNull
    private ApprovalMode mode;
    
    @NotEmpty
    @Valid
    private List<ApprovalParticipantRequest> participants;
}
