package uz.ithunter.crm.task;

import java.time.Instant;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.casemodule.engine.DeadlineCalculator;
import uz.ithunter.crm.casemodule.engine.activator.StageActivationContext;

/**
 * Creates a {@link Task} row for a newly activated stage (spec 7.3). Called exclusively by
 * {@code ExecutionStageActivator} and {@code FinalReviewStageActivator} — no other code should
 * produce tasks, so the constraint {@code uq_task_case_stage} stays a true uniqueness guarantee.
 *
 * <p>{@code TaskFactory} has no Spring transactionality of its own; it runs inside the caller's
 * (activator's → engine's) transaction, same pattern as {@code PrimaryCheckEvaluator}.
 */
@Component
public class TaskFactory {

    private final TaskRepository taskRepository;
    private final DeadlineCalculator deadlineCalculator;
    private final AuditWriter auditWriter;

    public TaskFactory(TaskRepository taskRepository, DeadlineCalculator deadlineCalculator,
                       AuditWriter auditWriter) {
        this.taskRepository = taskRepository;
        this.deadlineCalculator = deadlineCalculator;
        this.auditWriter = auditWriter;
    }

    /**
     * Creates and persists a {@link Task} from the activation context. Returns the saved task.
     * Idempotent under the {@code uq_task_case_stage} unique constraint — a second call throws a
     * DB unique violation, which is the correct signal that a task already exists.
     */
    public Task createFor(StageActivationContext ctx) {
        Task task = new Task();
        task.setCaseId(ctx.electronicCase().getId());
        task.setCaseStageId(ctx.stage().getId());
        task.setWorkflowStageId(ctx.stageConfig().getId());
        task.setTitle(ctx.stageConfig().getName());
        task.setDescription(null);
        task.setAssignedDepartmentId(ctx.stageConfig().getResponsibleDepartmentId());
        task.setStatus(TaskStatus.CREATED);
        task.setProcessingMode(ctx.electronicCase().getProcessingMode());
        task.setDeadline(deadlineCalculator.stageDueAt(
                ctx.stageConfig(), ctx.electronicCase().getProcessingMode(), Instant.now()));
        task = taskRepository.save(task);

        // Phase 11 gap-fix: TASK_CREATED was never emitted anywhere (confirmed by grep before this phase).
        auditWriter.write(AuditEvent.forCase(
                AuditAction.TASK_CREATED,
                "Task", task.getId(), ctx.electronicCase().getId(),
                null, null, null));

        return task;
    }
}
