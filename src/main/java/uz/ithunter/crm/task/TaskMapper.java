package uz.ithunter.crm.task;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.task.dto.TaskResponse;
import uz.ithunter.crm.task.dto.TaskResultResponse;
import uz.ithunter.crm.task.dto.TaskSummary;

@Component
public class TaskMapper {

    public TaskSummary toSummary(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskSummary(
                task.getId(),
                task.getCaseId(),
                task.getCaseStageId(),
                task.getTitle(),
                task.getAssignedDepartmentId(),
                task.getAssignedUserId(),
                task.getStatus(),
                task.getProcessingMode(),
                task.getDeadline(),
                task.isOverdue(),
                task.getVersion()
        );
    }

    public TaskResponse toResponse(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskResponse(
                task.getId(),
                task.getCaseId(),
                task.getCaseStageId(),
                task.getWorkflowStageId(),
                task.getTitle(),
                task.getDescription(),
                task.getAssignedDepartmentId(),
                task.getAssignedUserId(),
                task.getAssignedById(),
                task.getAssignedAt(),
                task.getStatus(),
                task.getProcessingMode(),
                task.getDeadline(),
                task.isOverdue(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getRevisionCount(),
                task.getVersion(),
                task.getCreatedAt()
        );
    }

    public TaskResultResponse toResultResponse(TaskResult result) {
        if (result == null) {
            return null;
        }
        return new TaskResultResponse(
                result.getId(),
                result.getTaskId(),
                result.getVersionNo(),
                result.getPayload(),
                result.getSummary(),
                result.getStatus(),
                result.getAuthorId(),
                result.getCreatedAt(),
                result.getSupersedesId(),
                result.getRevisionReason(),
                result.getApprovedById(),
                result.getApprovedAt()
        );
    }
}
