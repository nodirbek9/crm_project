package uz.ithunter.crm.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, UUID> {

    List<WorkflowStage> findByWorkflowId(UUID workflowId);

    void deleteByWorkflowId(UUID workflowId);
}
