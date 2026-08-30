package uz.ithunter.crm.work;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.PrimaryCheckRepository;
import uz.ithunter.crm.casemodule.engine.port.StageWorkRecorder;
import uz.ithunter.crm.document.DocumentRepository;
import uz.ithunter.crm.finance.Contract;
import uz.ithunter.crm.finance.ContractRepository;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.task.TaskRepository;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;

/**
 * Implements {@link StageWorkRecorder} — the integration hook already called by
 * {@link uz.ithunter.crm.casemodule.engine.WorkflowEngine#completeStage} via ObjectProvider.
 *
 * <p>Executor resolution strategy: for stages with a Task (EXECUTION, FINAL_REVIEW), the
 * task's assignedUserId is the executor. For PRIMARY_CHECK (which creates no task per
 * ASSUMPTIONS.md A37), we fall back to the most-recent PrimaryCheck.performedById — that person
 * is the one who actually did the work. If neither yields an executor (e.g., no task and no
 * primary check record), we skip recording: the caller (WorkflowEngine) passes no principal into
 * this port's signature, so we cannot invent one. A comment in the audit record would be needed
 * if this edge ever fires in production; for now, a null executor means no recording.
 *
 * <p>recordedById: because the StageWorkRecorder port's signature does not carry a principal
 * (it is a domain-event callback, not a direct HTTP action), we default recordedById to the
 * executor's userId. This is the most reasonable attribution: the person who completed the
 * work is also "who recorded it" in this system's context.
 */
@Component
public class PerformedWorkRecorder implements StageWorkRecorder {

    private final WorkflowStageRepository workflowStageRepository;
    private final TaskRepository taskRepository;
    private final PrimaryCheckRepository primaryCheckRepository;
    private final DocumentRepository documentRepository;
    private final ContractRepository contractRepository;
    private final PerformedWorkRepository performedWorkRepository;
    private final WorkTypeRepository workTypeRepository;
    private final AuditWriter auditWriter;

    public PerformedWorkRecorder(
            WorkflowStageRepository workflowStageRepository,
            TaskRepository taskRepository,
            PrimaryCheckRepository primaryCheckRepository,
            DocumentRepository documentRepository,
            ContractRepository contractRepository,
            PerformedWorkRepository performedWorkRepository,
            WorkTypeRepository workTypeRepository,
            AuditWriter auditWriter) {
        this.workflowStageRepository = workflowStageRepository;
        this.taskRepository = taskRepository;
        this.primaryCheckRepository = primaryCheckRepository;
        this.documentRepository = documentRepository;
        this.contractRepository = contractRepository;
        this.performedWorkRepository = performedWorkRepository;
        this.workTypeRepository = workTypeRepository;
        this.auditWriter = auditWriter;
    }

    @Override
    public void recordIfConfigured(ElectronicCase electronicCase, CaseStage stage) {
        WorkflowStage config = workflowStageRepository.findById(stage.getWorkflowStageId())
                .orElse(null);
        if (config == null || config.getWorkTypeId() == null) {
            // Stage has no work type — no countable work to record (e.g., ACCOUNTING, PAYMENT_CONTROL).
            return;
        }

        WorkType workType = workTypeRepository.findById(config.getWorkTypeId()).orElse(null);
        if (workType == null) {
            return;
        }

        // --- Resolve executor ---
        UUID executorUserId = resolveExecutor(electronicCase, stage);
        if (executorUserId == null) {
            // Cannot determine who did the work — skip silently. This is an edge case
            // (e.g., stage completed programmatically with no task and no primary check).
            return;
        }

        // --- Resolve supporting document version (optional, spec 8.3) ---
        // document.task_id is the linkage: find the Document whose task matches this stage's task,
        // then use its currentVersionId.
        UUID supportingDocVersionId = taskRepository.findByCaseStageId(stage.getId())
                .flatMap(task -> documentRepository.findFirstByTask_Id(task.getId()))
                .map(doc -> doc.getCurrentVersionId())
                .orElse(null);

        // --- Resolve contract amount bracket (spec 8.4) ---
        ContractAmountBracket bracket = null;
        if (workType.isRequiresContractAmountBracket()) {
            BigDecimal amount = contractRepository.findByCaseId(electronicCase.getId())
                    .map(c -> c.getActualAmount() != null ? c.getActualAmount() : c.getCalculatedAmount())
                    .orElse(BigDecimal.ZERO);
            bracket = ContractAmountBracketResolver.resolve(amount);
        }

        // --- Upsert: look up existing row by (caseId, workTypeId, caseStageId) ---
        Optional<PerformedWork> existing = performedWorkRepository
                .findByCaseIdAndWorkTypeIdAndCaseStageId(
                        electronicCase.getId(), config.getWorkTypeId(), stage.getId());

        if (existing.isPresent()) {
            // PW-02: revision cycle — only update the supporting document, nothing else.
            PerformedWork pw = existing.get();
            pw.setSupportingDocumentVersionId(supportingDocVersionId);
            performedWorkRepository.save(pw);
            auditWriter.write(AuditEvent.forCase(
                    AuditAction.PERFORMED_WORK_RECORDED,
                    "PerformedWork", pw.getId(), electronicCase.getId(),
                    null, null, "revision-cycle update"));
        } else {
            // PW-01: new row
            PerformedWork pw = new PerformedWork();
            pw.setCaseId(electronicCase.getId());
            pw.setWorkTypeId(config.getWorkTypeId());
            pw.setCaseStageId(stage.getId());
            pw.setWorkflowStageId(stage.getWorkflowStageId());
            pw.setServiceId(electronicCase.getServiceId());
            pw.setDepartmentId(config.getResponsibleDepartmentId() != null
                    ? config.getResponsibleDepartmentId()
                    : electronicCase.getMainResponsibleDepartmentId());
            pw.setExecutorUserId(executorUserId);
            // performed_work.processing_mode is NOT NULL, but electronic_case.processing_mode
            // stays null until an accountant explicitly calls POST .../processing-mode - a stage
            // can complete before that happens (or on a route where nobody ever calls it). Same
            // "unset means TRADITIONAL" convention DeadlineCalculator already uses, not a guess
            // invented here.
            pw.setProcessingMode(electronicCase.getProcessingMode() != null
                    ? electronicCase.getProcessingMode() : ProcessingMode.TRADITIONAL);
            pw.setPerformedAt(Instant.now());
            pw.setRecordedAt(Instant.now());
            pw.setRecordedById(executorUserId);
            pw.setSupportingDocumentVersionId(supportingDocVersionId);
            pw.setContractAmountBracket(bracket);
            pw = performedWorkRepository.save(pw);
            auditWriter.write(AuditEvent.forCase(
                    AuditAction.PERFORMED_WORK_RECORDED,
                    "PerformedWork", pw.getId(), electronicCase.getId(),
                    null, null, null));
        }
    }

    private UUID resolveExecutor(ElectronicCase electronicCase, CaseStage stage) {
        // Try Task first (EXECUTION/FINAL_REVIEW stages)
        return taskRepository.findByCaseStageId(stage.getId())
                .map(task -> task.getAssignedUserId())
                .filter(uid -> uid != null)
                // Fall back to PRIMARY_CHECK performer for stages without a task
                .orElseGet(() -> primaryCheckRepository
                        .findFirstByCaseIdOrderByAttemptNoDesc(electronicCase.getId())
                        .map(pc -> pc.getPerformedById())
                        .orElse(null));
    }
}
