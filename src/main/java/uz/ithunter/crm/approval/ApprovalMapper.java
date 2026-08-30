package uz.ithunter.crm.approval;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.approval.dto.ApprovalRoundResponse;
import uz.ithunter.crm.approval.dto.ApprovalTaskResponse;
import uz.ithunter.crm.approval.dto.ApprovalTaskSummary;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ApprovalMapper {

    public ApprovalTaskResponse toTaskResponse(ApprovalTask task) {
        ApprovalTaskResponse response = new ApprovalTaskResponse();
        response.setId(task.getId());
        response.setApprovalRoundId(task.getApprovalRound().getId());
        response.setParticipantKind(task.getParticipantKind().name());
        response.setParticipantUserId(task.getParticipantUserId());
        response.setParticipantDepartmentId(task.getParticipantDepartmentId());
        response.setRequired(task.isRequired());
        response.setSequenceNo(task.getSequenceNo());
        response.setStatus(task.getStatus().name());
        response.setComment(task.getComment());
        response.setDecidedById(task.getDecidedById());
        response.setDecidedAt(task.getDecidedAt());
        response.setDueAt(task.getDueAt());
        response.setCreatedAt(task.getCreatedAt());
        return response;
    }

    public ApprovalTaskSummary toTaskSummary(ApprovalTask task) {
        ApprovalTaskSummary summary = new ApprovalTaskSummary();
        summary.setId(task.getId());
        summary.setApprovalRoundId(task.getApprovalRound().getId());
        summary.setDocumentVersionId(task.getApprovalRound().getDocumentVersion().getId());
        summary.setCaseId(task.getApprovalRound().getElectronicCase().getId());
        summary.setStatus(task.getStatus().name());
        summary.setCreatedAt(task.getCreatedAt());
        return summary;
    }

    public ApprovalRoundResponse toRoundResponse(ApprovalRound round, List<ApprovalTask> tasks) {
        ApprovalRoundResponse response = new ApprovalRoundResponse();
        response.setId(round.getId());
        response.setDocumentVersionId(round.getDocumentVersion().getId());
        response.setCaseId(round.getElectronicCase().getId());
        response.setMode(round.getMode().name());
        response.setRoundNo(round.getRoundNo());
        response.setStatus(round.getStatus().name());
        response.setInitiatedById(round.getInitiatedById());
        response.setInitiatedAt(round.getInitiatedAt());
        response.setCompletedAt(round.getCompletedAt());
        if (tasks != null) {
            response.setTasks(tasks.stream().map(this::toTaskResponse).collect(Collectors.toList()));
        }
        return response;
    }
}
