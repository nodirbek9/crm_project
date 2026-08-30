package uz.ithunter.crm.task;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.port.CaseTaskAssignmentLookup;

/**
 * The Phase 9 implementation of the seam {@code casemodule.port.CaseTaskAssignmentLookup}
 * declares. Once this bean exists, Spring wires it into {@code CaseAccessPolicy}'s
 * {@code ObjectProvider} automatically - no change needed there.
 */
@Component
public class TaskCaseAssignmentLookup implements CaseTaskAssignmentLookup {

    private final TaskRepository taskRepository;

    public TaskCaseAssignmentLookup(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public boolean existsAssignedToUser(UUID caseId, UUID userId) {
        return taskRepository.existsByCaseIdAndAssignedUserId(caseId, userId);
    }

    @Override
    public boolean existsAssignedToDepartment(UUID caseId, UUID departmentId) {
        return taskRepository.existsByCaseIdAndAssignedDepartmentId(caseId, departmentId);
    }
}
