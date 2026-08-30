package uz.ithunter.crm.casemodule.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageRepository;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.casemodule.engine.activator.StageActivationContext;
import uz.ithunter.crm.casemodule.engine.activator.StageActivatorRegistry;
import uz.ithunter.crm.casemodule.engine.condition.TransitionContext;
import uz.ithunter.crm.casemodule.engine.port.StageWorkRecorder;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowRepository;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;
import uz.ithunter.crm.workflow.WorkflowTransition;
import uz.ithunter.crm.workflow.WorkflowTransitionRepository;

/**
 * The engine (WORKFLOW_ENGINE_DESIGN.md 3-7). Three public verbs -
 * {@link #activateStage}, {@link #completeStage}, {@link #advance} - plus the two operations
 * registration needs: {@link #materialiseStages} and {@link #activateEntryStages}.
 *
 * <p>Every method is {@code @Transactional(REQUIRED)}, never {@code REQUIRES_NEW}. The engine always
 * runs inside the caller's transaction so that a business change and its audit rows can never diverge:
 * if the command rolls back, so does the claim that it happened (WORKFLOW_ENGINE_DESIGN.md 12).
 *
 * <p><b>Idempotency is structural, not defensive.</b> {@code uq_case_stage (case_id,
 * workflow_stage_id)} means a stage instance exists at most once, so a replayed activation finds a row
 * that is already ACTIVE and returns it untouched; {@code activation_count} therefore counts real
 * activations and test C-02 can assert it equals 1 after a race. The same shape makes a replayed
 * {@code completeStage} a no-op that does not advance the route twice (Phase 9's DoD).
 */
@Service
public class WorkflowEngine {

    private final ElectronicCaseRepository electronicCaseRepository;
    private final CaseStageRepository caseStageRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStageRepository workflowStageRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final TransitionEvaluator transitionEvaluator;
    private final StageActivatorRegistry stageActivatorRegistry;
    private final CaseLifecycleResolver caseLifecycleResolver;
    private final DeadlineCalculator deadlineCalculator;
    private final AuditWriter auditWriter;
    private final ObjectProvider<StageWorkRecorder> stageWorkRecorder;

    public WorkflowEngine(ElectronicCaseRepository electronicCaseRepository,
            CaseStageRepository caseStageRepository,
            WorkflowRepository workflowRepository,
            WorkflowStageRepository workflowStageRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            TransitionEvaluator transitionEvaluator,
            StageActivatorRegistry stageActivatorRegistry,
            CaseLifecycleResolver caseLifecycleResolver,
            DeadlineCalculator deadlineCalculator,
            AuditWriter auditWriter,
            ObjectProvider<StageWorkRecorder> stageWorkRecorder) {
        this.electronicCaseRepository = electronicCaseRepository;
        this.caseStageRepository = caseStageRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStageRepository = workflowStageRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.transitionEvaluator = transitionEvaluator;
        this.stageActivatorRegistry = stageActivatorRegistry;
        this.caseLifecycleResolver = caseLifecycleResolver;
        this.deadlineCalculator = deadlineCalculator;
        this.auditWriter = auditWriter;
        this.stageWorkRecorder = stageWorkRecorder;
    }

    /**
     * Creates one {@code case_stage} per {@code workflow_stage} of the pinned version, all PENDING
     * (WORKFLOW_ENGINE_DESIGN.md 3, spec 4.4).
     *
     * <p>All of them up front, not lazily as the route progresses. That is what lets the parallel gate
     * be a single indexed read over sibling rows instead of a graph traversal, and it is what gives the
     * applicant-facing timeline something to show for stages not yet reached.
     *
     * <p>{@code parallel_group} and {@code required} are copied onto the case rows so the gate query of
     * spec 7.14 touches one table and can take its row locks there.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<CaseStage> materialiseStages(ElectronicCase electronicCase) {
        List<WorkflowStage> configs = workflowStageRepository.findByWorkflowId(electronicCase.getWorkflowId());
        if (configs.isEmpty()) {
            throw new IllegalStateTransitionException("WORKFLOW_HAS_NO_STAGES",
                    "Workflow " + electronicCase.getWorkflowId() + " has no stages to materialise");
        }
        List<CaseStage> created = new ArrayList<>(configs.size());
        for (WorkflowStage config : configs) {
            CaseStage stage = new CaseStage();
            stage.setCaseId(electronicCase.getId());
            stage.setWorkflowStageId(config.getId());
            stage.setStatus(CaseStageStatus.PENDING);
            stage.setParallelGroup(config.getParallelGroup());
            stage.setRequired(config.isRequired());
            stage.setActivationCount(0);
            created.add(stage);
        }
        return caseStageRepository.saveAll(created);
    }

    /**
     * Traverses the entry transitions ({@code from_stage_id IS NULL}) and activates what matches -
     * the last step of registration (spec 1.4).
     *
     * <p>A list, not a single stage: nothing in the model forbids a route from opening two branches
     * immediately, and a route that does would silently lose one of them if this returned one stage.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<CaseStage> activateEntryStages(ElectronicCase electronicCase) {
        return traverse(electronicCase, null);
    }

    /**
     * WORKFLOW_ENGINE_DESIGN.md 4. Returns the stage row in its (possibly unchanged) state.
     *
     * <p>Two deliberate departures from the pseudocode's statement order, both to keep the row
     * self-consistent at the end of the method rather than in the middle of it:
     * <ul>
     *   <li>{@code current_stage_id} is recomputed <b>after</b> the activator runs, because an
     *       activator may close its own stage - {@code COMPLETION} does - and the pointer must reflect
     *       what is actually open;</li>
     *   <li>the {@code STAGE_ACTIVATED} audit row is written last, so it records the state that was
     *       really committed, including any deadline the activator set.</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public CaseStage activateStage(ElectronicCase electronicCase, UUID workflowStageId) {
        CaseStage stage = caseStageRepository
                .findByCaseIdAndWorkflowStageId(electronicCase.getId(), workflowStageId)
                .orElseThrow(() -> new NotFoundException("STAGE_NOT_MATERIALISED",
                        "Case " + electronicCase.getId() + " has no stage row for workflow stage "
                                + workflowStageId));

        // IDEMPOTENT no-op: a replayed command, or a second parallel sibling closing the same gate.
        if (stage.getStatus() == CaseStageStatus.ACTIVE || stage.getStatus() == CaseStageStatus.COMPLETED) {
            return stage;
        }

        WorkflowStage config = requireStageConfig(workflowStageId);
        Workflow workflow = requireWorkflow(electronicCase);
        Instant now = Instant.now();

        stage.setStatus(CaseStageStatus.ACTIVE);
        stage.setActivatedAt(now);
        stage.setActivationCount(stage.getActivationCount() + 1);
        stage.setDueAt(deadlineCalculator.stageDueAt(config, electronicCase.getProcessingMode(), now));
        stage.setOverdue(false);

        CaseStatus lifecycle = caseLifecycleResolver.lifecycleFor(config.getStageType());
        if (lifecycle != null) {
            electronicCase.setStatus(lifecycle);
        }

        stageActivatorRegistry.resolve(config.getStageType()).ifPresent(activator -> activator
                .onActivate(new StageActivationContext(electronicCase, stage, config, workflow)));

        caseStageRepository.save(stage);
        refreshCurrentStage(electronicCase);
        electronicCaseRepository.save(electronicCase);

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("stageCode", config.getCode());
        newValue.put("stageType", config.getStageType().name());
        newValue.put("caseStatus", electronicCase.getStatus().name());
        newValue.put("activationCount", stage.getActivationCount());
        if (stage.getDueAt() != null) {
            newValue.put("dueAt", stage.getDueAt().toString());
        }
        auditWriter.write(AuditEvent.forCase(AuditAction.STAGE_ACTIVATED, "CaseStage", stage.getId(),
                electronicCase.getId(), null, newValue, null));
        return stage;
    }

    /** Convenience overload for callers that only hold the id. */
    @Transactional(propagation = Propagation.REQUIRED)
    public CaseStage activateStage(UUID caseId, UUID workflowStageId) {
        return activateStage(requireCase(caseId), workflowStageId);
    }

    /**
     * WORKFLOW_ENGINE_DESIGN.md 5. Completing a stage is what makes the route move: the outgoing
     * transitions are evaluated immediately afterwards, inside the same transaction.
     *
     * <p>Completing a stage that is not ACTIVE is {@code 422 INVALID_STATE_TRANSITION} rather than a
     * silent no-op, because it means the caller's view of the case is stale - except when the stage is
     * already COMPLETED, which is a replay and must return the same answer without advancing again.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public CaseStage completeStage(UUID caseId, UUID workflowStageId) {
        ElectronicCase electronicCase = requireCase(caseId);
        CaseStage stage = caseStageRepository.findByCaseIdAndWorkflowStageId(caseId, workflowStageId)
                .orElseThrow(() -> new NotFoundException("STAGE_NOT_MATERIALISED",
                        "Case " + caseId + " has no stage row for workflow stage " + workflowStageId));

        if (stage.getStatus() == CaseStageStatus.COMPLETED) {
            return stage; // IDEMPOTENT: does not advance the workflow a second time
        }
        if (stage.getStatus() != CaseStageStatus.ACTIVE) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION",
                    "Stage must be ACTIVE to complete, but is " + stage.getStatus());
        }

        // C-02 (Phase 12): if this stage has parallel siblings, lock the WHOLE group - in the same
        // ascending-id order every transaction uses - BEFORE this transaction's own row gets its
        // status flipped. Locking only-my-own-row first (via the save()+audit-flush below) and only
        // THEN reaching for the group via advance() -> lockRequiredSiblings() lets two siblings each
        // grab their own row's exclusive lock first and then deadlock waiting on each other's -
        // reproduced by a genuinely simultaneous C-02. Acquiring the group lock up front, before any
        // row (including this one) is touched, makes every transaction request the same lock
        // sequence, so the second one simply queues instead of cycling.
        if (stage.getParallelGroup() != null) {
            caseStageRepository.lockRequiredSiblings(caseId, stage.getParallelGroup());
        }

        Instant now = Instant.now();
        stage.setStatus(CaseStageStatus.COMPLETED);
        stage.setCompletedAt(now);
        caseStageRepository.save(stage);

        WorkflowStage config = requireStageConfig(workflowStageId);
        Map<String, Object> newValue = new HashMap<>();
        newValue.put("stageCode", config.getCode());
        newValue.put("completedAt", now.toString());
        auditWriter.write(AuditEvent.forCase(AuditAction.STAGE_COMPLETED, "CaseStage", stage.getId(),
                caseId, Map.of("status", CaseStageStatus.ACTIVE.name()), newValue, null));

        // spec 8: performed works are recorded from the engine, not from the task endpoint, so a stage
        // completed by any route records the work exactly once. Phase 11 supplies the implementation.
        StageWorkRecorder recorder = stageWorkRecorder.getIfAvailable();
        if (recorder != null) {
            recorder.recordIfConfigured(electronicCase, stage);
        }

        advance(electronicCase, workflowStageId);
        return stage;
    }

    /**
     * Evaluates every outgoing transition of {@code fromWorkflowStageId} in {@code sequence} order and
     * activates each target whose condition matches.
     *
     * <p>There is deliberately <b>no</b> {@code break} after the first match. A fan-out into a parallel
     * group is expressed as several transitions out of the same stage, all with the same condition, and
     * stopping at the first would open one branch of three (test W-02).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<CaseStage> advance(ElectronicCase electronicCase, UUID fromWorkflowStageId) {
        return traverse(electronicCase, fromWorkflowStageId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<CaseStage> advance(UUID caseId, UUID fromWorkflowStageId) {
        return traverse(requireCase(caseId), fromWorkflowStageId);
    }

    private List<CaseStage> traverse(ElectronicCase electronicCase, UUID fromWorkflowStageId) {
        Workflow workflow = requireWorkflow(electronicCase);
        List<WorkflowTransition> outgoing = workflowTransitionRepository
                .findByWorkflowId(workflow.getId()).stream()
                .filter(transition -> Objects.equals(transition.getFromStageId(), fromWorkflowStageId))
                .sorted(Comparator.comparingInt(WorkflowTransition::getSequence))
                .toList();

        // The lookup closure is where the pessimistic lock of spec 7.14 lives: the handler stays pure,
        // the transaction-aware SELECT ... FOR UPDATE stays here.
        TransitionContext context = new TransitionContext(electronicCase, workflow,
                group -> caseStageRepository.lockRequiredSiblings(electronicCase.getId(), group));

        List<CaseStage> activated = new ArrayList<>();
        for (WorkflowTransition transition : outgoing) {
            if (transitionEvaluator.matches(transition, context)) {
                activated.add(activateStage(electronicCase, transition.getToStageId()));
            }
        }
        return activated;
    }

    /**
     * {@code current_stage_id} is the position in the route, and it is NULL whenever the answer is not
     * a single stage (PLAN_REVIEW M1 / FIX 7, test W-03). Zero active stages means the case is waiting
     * on something outside the route or has finished; two or more means a parallel group is open, and
     * naming one of them would be arbitrary. The applicant is unaffected either way - they see the
     * mapped external stage (spec 5.11).
     */
    private void refreshCurrentStage(ElectronicCase electronicCase) {
        List<CaseStage> active = caseStageRepository
                .findByCaseIdAndStatus(electronicCase.getId(), CaseStageStatus.ACTIVE);
        electronicCase.setCurrentStageId(
                active.size() == 1 ? active.get(0).getWorkflowStageId() : null);
    }

    private ElectronicCase requireCase(UUID caseId) {
        return electronicCaseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found"));
    }

    private Workflow requireWorkflow(ElectronicCase electronicCase) {
        return workflowRepository.findById(electronicCase.getWorkflowId())
                .orElseThrow(() -> new NotFoundException("WORKFLOW_NOT_FOUND",
                        "Pinned workflow " + electronicCase.getWorkflowId() + " no longer exists"));
    }

    private WorkflowStage requireStageConfig(UUID workflowStageId) {
        return workflowStageRepository.findById(workflowStageId)
                .orElseThrow(() -> new NotFoundException("WORKFLOW_STAGE_NOT_FOUND",
                        "Workflow stage " + workflowStageId + " not found"));
    }
}
