package uz.ithunter.crm.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, UUID> {

    List<WorkflowTransition> findByWorkflowId(UUID workflowId);

    void deleteByWorkflowId(UUID workflowId);
}
