package uz.ithunter.crm.task;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageRepository;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.casemodule.engine.DeadlineCalculator;
import uz.ithunter.crm.finance.port.OverduePaymentTaskCreator;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;
import uz.ithunter.crm.workflow.WorkflowTransitionRepository;

/**
 * Implements the {@link OverduePaymentTaskCreator} port that
 * {@link uz.ithunter.crm.finance.PaymentWaitingScheduler} depends on.
 *
 * <p>An overdue payment means a manager must decide: wait, accept a partial payment and start
 * execution anyway, or reject the case (spec 12.9). The right stage for this decision task is the
 * live PAYMENT_CONTROL {@code case_stage}, because that is where the case is currently blocked.
 *
 * <p>ASSUMPTIONS.md A38: if a task already exists for the PAYMENT_CONTROL stage (e.g. the scheduler
 * fires twice), this implementation is a no-op — the existing task is the decision task and there
 * is nothing more to create. The idempotency matches the scheduler's own "flag once" pattern.
 */
@Component
public class OverduePaymentTaskCreatorImpl implements OverduePaymentTaskCreator {

    private final ElectronicCaseRepository electronicCaseRepository;
    private final CaseStageRepository caseStageRepository;
    private final WorkflowStageRepository workflowStageRepository;
    private final TaskRepository taskRepository;
    private final DeadlineCalculator deadlineCalculator;

    public OverduePaymentTaskCreatorImpl(ElectronicCaseRepository electronicCaseRepository,
            CaseStageRepository caseStageRepository,
            WorkflowStageRepository workflowStageRepository,
            TaskRepository taskRepository,
            DeadlineCalculator deadlineCalculator) {
        this.electronicCaseRepository = electronicCaseRepository;
        this.caseStageRepository = caseStageRepository;
        this.workflowStageRepository = workflowStageRepository;
        this.taskRepository = taskRepository;
        this.deadlineCalculator = deadlineCalculator;
    }

    @Override
    public void createDecisionTask(UUID caseId, UUID paymentId) {
        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElse(null);
        if (electronicCase == null) {
            return;
        }
        // Find the active PAYMENT_CONTROL stage (there should be exactly one when this is called).
        CaseStage activeStage = caseStageRepository.findByCaseIdAndStatus(caseId, CaseStageStatus.ACTIVE)
                .stream()
                .filter(s -> {
                    WorkflowStage wStage = workflowStageRepository.findById(s.getWorkflowStageId()).orElse(null);
                    return wStage != null && wStage.getStageType() == uz.ithunter.crm.workflow.StageType.PAYMENT_CONTROL;
                })
                .findFirst()
                .orElse(null);
        if (activeStage == null) {
            return;
        }
        // Idempotent: if a task already exists for this stage, do nothing (A38).
        if (taskRepository.findByCaseStageId(activeStage.getId()).isPresent()) {
            return;
        }
        WorkflowStage stageConfig = workflowStageRepository.findById(activeStage.getWorkflowStageId())
                .orElse(null);
        if (stageConfig == null) {
            return;
        }
        Task task = new Task();
        task.setCaseId(caseId);
        task.setCaseStageId(activeStage.getId());
        task.setWorkflowStageId(activeStage.getWorkflowStageId());
        task.setTitle("Overdue payment decision — case " + electronicCase.getCaseNumber());
        task.setDescription("Payment " + paymentId + " is overdue. Manager action required (spec 12.9).");
        task.setAssignedDepartmentId(stageConfig.getResponsibleDepartmentId() != null
                ? stageConfig.getResponsibleDepartmentId()
                : electronicCase.getMainResponsibleDepartmentId());
        task.setStatus(TaskStatus.CREATED);
        task.setProcessingMode(electronicCase.getProcessingMode());
        task.setDeadline(deadlineCalculator.stageDueAt(
                stageConfig, electronicCase.getProcessingMode(), Instant.now()));
        taskRepository.save(task);
    }
}
