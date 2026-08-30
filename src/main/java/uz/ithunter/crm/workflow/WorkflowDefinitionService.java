package uz.ithunter.crm.workflow;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.shared.exception.ConflictException;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.work.WorkTypeRepository;
import uz.ithunter.crm.workflow.dto.StageInput;
import uz.ithunter.crm.workflow.dto.TransitionInput;
import uz.ithunter.crm.workflow.dto.UpdateWorkflowStagesRequest;
import uz.ithunter.crm.workflow.dto.WorkflowResponse;
import uz.ithunter.crm.workflow.dto.WorkflowStageResponse;
import uz.ithunter.crm.workflow.dto.WorkflowSummary;
import uz.ithunter.crm.workflow.dto.WorkflowTransitionResponse;

/**
 * WORKFLOW_ENGINE_DESIGN.md 2: copy-on-write versioning. A published (non-DRAFT) row and
 * everything it owns is immutable - {@link #updateStages} is the only mutation path and it refuses
 * anything but a DRAFT (this is **W-12**). {@link #publish} is where {@link WorkflowPublishValidator}
 * runs and where the previous ACTIVE version gets retired in the same transaction; **C-07**'s
 * concurrency guarantee comes from {@code uq_workflow_one_active} plus the existing generic
 * {@code DataIntegrityViolationException} to 409 fallback in {@code GlobalExceptionHandler} - no
 * extra locking code needed here.
 */
@Service
public class WorkflowDefinitionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStageRepository workflowStageRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final ExternalStageRepository externalStageRepository;
    private final WorkTypeRepository workTypeRepository;
    private final WorkflowPublishValidator publishValidator;
    private final AuditWriter auditWriter;
    private final EntityManager entityManager;

    public WorkflowDefinitionService(WorkflowRepository workflowRepository,
            WorkflowStageRepository workflowStageRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            ExternalStageRepository externalStageRepository, WorkTypeRepository workTypeRepository,
            WorkflowPublishValidator publishValidator, AuditWriter auditWriter, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.workflowRepository = workflowRepository;
        this.workflowStageRepository = workflowStageRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.externalStageRepository = externalStageRepository;
        this.workTypeRepository = workTypeRepository;
        this.publishValidator = publishValidator;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public List<WorkflowSummary> list() {
        return workflowRepository.findAll().stream()
                .map(w -> new WorkflowSummary(w.getId(), w.getServiceId(), w.getCode(), w.getVersion(),
                        w.getName(), w.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public WorkflowResponse createDraftVersion(String code) {
        Workflow active = workflowRepository.findByCodeAndStatus(code, WorkflowStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("No ACTIVE workflow found for code " + code));

        int newVersion = workflowRepository.findMaxVersionByCode(code) + 1;

        Workflow draft = new Workflow();
        draft.setServiceId(active.getServiceId());
        draft.setCode(active.getCode());
        draft.setVersion(newVersion);
        draft.setName(active.getName());
        draft.setDescription(active.getDescription());
        draft.setStatus(WorkflowStatus.DRAFT);
        draft.setMainResponsibleDepartmentId(active.getMainResponsibleDepartmentId());
        draft.setExpeditedAllowed(active.isExpeditedAllowed());
        draft.setContractRequired(active.isContractRequired());
        draft.setPaymentRequired(active.isPaymentRequired());
        draft.setAllowExecutionBeforeFullPayment(active.isAllowExecutionBeforeFullPayment());
        draft.setPaymentWaitingDays(active.getPaymentWaitingDays());
        draft.setTotalDeadlineDays(active.getTotalDeadlineDays());
        draft.setApprovalRequired(active.isApprovalRequired());
        draft = workflowRepository.save(draft);

        Map<UUID, UUID> stageIdMap = new HashMap<>();
        for (WorkflowStage source : workflowStageRepository.findByWorkflowId(active.getId())) {
            WorkflowStage copy = new WorkflowStage();
            copy.setWorkflowId(draft.getId());
            copy.setCode(source.getCode());
            copy.setName(source.getName());
            copy.setStageType(source.getStageType());
            copy.setSequence(source.getSequence());
            copy.setParallelGroup(source.getParallelGroup());
            copy.setRequired(source.isRequired());
            copy.setExternalStageId(source.getExternalStageId());
            copy.setInternalStatusLabel(source.getInternalStatusLabel());
            copy.setResponsibleRoleCode(source.getResponsibleRoleCode());
            copy.setResponsibleDepartmentId(source.getResponsibleDepartmentId());
            copy.setAssignmentMode(source.getAssignmentMode());
            copy.setDeadlineDays(source.getDeadlineDays());
            copy.setExpeditedDeadlineDays(source.getExpeditedDeadlineDays());
            copy.setWorkTypeId(source.getWorkTypeId());
            copy.setProducesDocumentType(source.getProducesDocumentType());
            copy.setRequiresResult(source.isRequiresResult());
            copy.setRevisionAllowed(source.isRevisionAllowed());
            copy.setApprovalRequired(source.isApprovalRequired());
            copy.setApprovalMode(source.getApprovalMode());
            copy = workflowStageRepository.save(copy);
            stageIdMap.put(source.getId(), copy.getId());
        }

        for (WorkflowTransition source : workflowTransitionRepository.findByWorkflowId(active.getId())) {
            WorkflowTransition copy = new WorkflowTransition();
            copy.setWorkflowId(draft.getId());
            copy.setFromStageId(source.getFromStageId() == null ? null : stageIdMap.get(source.getFromStageId()));
            copy.setToStageId(stageIdMap.get(source.getToStageId()));
            copy.setConditionType(source.getConditionType());
            copy.setConditionValue(source.getConditionValue());
            copy.setSequence(source.getSequence());
            workflowTransitionRepository.save(copy);
        }

        return toResponse(draft);
    }

    @Transactional
    public WorkflowResponse updateStages(UUID id, UpdateWorkflowStagesRequest request) {
        Workflow workflow = findOrThrow(id);
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            // API_SPEC.md 9 lists WORKFLOW_VERSION_IMMUTABLE under 409 (immutability), not 422.
            throw new ConflictException("WORKFLOW_VERSION_IMMUTABLE",
                    "Only a DRAFT workflow version's stages can be edited");
        }

        workflowTransitionRepository.deleteByWorkflowId(id);
        workflowStageRepository.deleteByWorkflowId(id);
        // Force the deletes to hit the DB before any insert below: uq_workflow_stage_sequence and
        // uq_transition_single_entry are unique constraints that a deferred flush would otherwise
        // trip over (the old and new rows briefly coexisting in the same flush).
        entityManager.flush();

        Map<String, UUID> stageIdByCode = new HashMap<>();
        for (StageInput input : request.stages()) {
            WorkflowStage stage = new WorkflowStage();
            stage.setWorkflowId(id);
            stage.setCode(input.code());
            stage.setName(input.name());
            stage.setStageType(parseEnum(StageType.class, input.stageType(), "stageType"));
            stage.setSequence(input.sequence());
            stage.setParallelGroup(input.parallelGroup());
            stage.setRequired(input.required());
            stage.setExternalStageId(externalStageRepository.findByCode(input.externalStageCode())
                    .orElseThrow(() -> new ValidationException("VALIDATION_FAILED", "Unknown external stage code: " + input.externalStageCode()))
                    .getId());
            stage.setInternalStatusLabel(input.internalStatusLabel());
            stage.setResponsibleRoleCode(input.responsibleRoleCode());
            stage.setResponsibleDepartmentId(input.responsibleDepartmentId());
            stage.setAssignmentMode(parseEnum(AssignmentMode.class, input.assignmentMode(), "assignmentMode"));
            stage.setDeadlineDays(input.deadlineDays());
            stage.setExpeditedDeadlineDays(input.expeditedDeadlineDays());
            if (input.workTypeCode() != null) {
                stage.setWorkTypeId(workTypeRepository.findByCode(input.workTypeCode())
                        .orElseThrow(() -> new ValidationException("VALIDATION_FAILED", "Unknown work type code: " + input.workTypeCode()))
                        .getId());
            }
            stage.setProducesDocumentType(input.producesDocumentType());
            stage.setRequiresResult(input.requiresResult());
            stage.setRevisionAllowed(input.revisionAllowed());
            stage.setApprovalRequired(input.approvalRequired());
            stage.setApprovalMode(input.approvalMode() == null ? null : parseEnum(ApprovalMode.class, input.approvalMode(), "approvalMode"));
            stage = workflowStageRepository.save(stage);
            stageIdByCode.put(input.code(), stage.getId());
        }

        for (TransitionInput input : request.transitions()) {
            WorkflowTransition transition = new WorkflowTransition();
            transition.setWorkflowId(id);
            transition.setFromStageId(input.fromStageCode() == null ? null : requireStageId(stageIdByCode, input.fromStageCode()));
            transition.setToStageId(requireStageId(stageIdByCode, input.toStageCode()));
            transition.setConditionType(parseEnum(ConditionType.class, input.conditionType(), "conditionType"));
            transition.setConditionValue(input.conditionValue());
            transition.setSequence(input.sequence());
            workflowTransitionRepository.save(transition);
        }

        return toResponse(workflow);
    }

    @Transactional
    public WorkflowResponse publish(UUID id, CustomUserPrincipal principal) {
        Workflow draft = findOrThrow(id);
        if (draft.getStatus() != WorkflowStatus.DRAFT) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION", "Only a DRAFT workflow version can be published");
        }

        List<WorkflowStage> stages = workflowStageRepository.findByWorkflowId(id);
        List<WorkflowTransition> transitions = workflowTransitionRepository.findByWorkflowId(id);
        publishValidator.validate(stages, transitions);

        // Retire the previous ACTIVE row FIRST, while the draft is still DRAFT: uq_workflow_one_active
        // would reject an intermediate state where both rows are ACTIVE at once, and Hibernate can
        // auto-flush between these two saves before this method's own commit.
        workflowRepository.findByCodeAndStatus(draft.getCode(), WorkflowStatus.ACTIVE).ifPresent(previousActive -> {
            previousActive.setStatus(WorkflowStatus.RETIRED);
            workflowRepository.save(previousActive);
            auditWriter.write(AuditEvent.administrative(AuditAction.WORKFLOW_RETIRED, "Workflow",
                    previousActive.getId(), Map.of("status", "ACTIVE"), Map.of("status", "RETIRED")));
        });

        Instant now = Instant.now();
        draft.setStatus(WorkflowStatus.ACTIVE);
        draft.setPublishedAt(now);
        draft.setPublishedBy(principal.userId());
        draft = workflowRepository.save(draft);

        auditWriter.write(AuditEvent.administrative(AuditAction.WORKFLOW_PUBLISHED, "Workflow", draft.getId(),
                Map.of("status", "DRAFT"), Map.of("status", "ACTIVE", "version", draft.getVersion())));

        return toResponse(draft);
    }

    @Transactional
    public WorkflowResponse retire(UUID id) {
        Workflow workflow = findOrThrow(id);
        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION", "Only an ACTIVE workflow version can be retired");
        }
        workflow.setStatus(WorkflowStatus.RETIRED);
        workflow = workflowRepository.save(workflow);
        auditWriter.write(AuditEvent.administrative(AuditAction.WORKFLOW_RETIRED, "Workflow", workflow.getId(),
                Map.of("status", "ACTIVE"), Map.of("status", "RETIRED")));
        return toResponse(workflow);
    }

    private UUID requireStageId(Map<String, UUID> stageIdByCode, String code) {
        UUID id = stageIdByCode.get(code);
        if (id == null) {
            throw new ValidationException("VALIDATION_FAILED", "Transition references unknown stage code: " + code);
        }
        return id;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("VALIDATION_FAILED", "Invalid " + fieldName + ": " + value);
        }
    }

    private Workflow findOrThrow(UUID id) {
        return workflowRepository.findById(id).orElseThrow(() -> new NotFoundException("Workflow not found"));
    }

    private WorkflowResponse toResponse(Workflow w) {
        List<WorkflowStageResponse> stages = workflowStageRepository.findByWorkflowId(w.getId()).stream()
                .map(this::toStageResponse)
                .toList();
        List<WorkflowTransitionResponse> transitions = workflowTransitionRepository.findByWorkflowId(w.getId()).stream()
                .map(t -> new WorkflowTransitionResponse(t.getId(), t.getFromStageId(), t.getToStageId(),
                        t.getConditionType().name(), t.getConditionValue(), t.getSequence()))
                .toList();
        return new WorkflowResponse(w.getId(), w.getServiceId(), w.getCode(), w.getVersion(), w.getName(),
                w.getDescription(), w.getStatus().name(), w.getMainResponsibleDepartmentId(), w.isExpeditedAllowed(),
                w.isContractRequired(), w.isPaymentRequired(), w.isAllowExecutionBeforeFullPayment(),
                w.getPaymentWaitingDays(), w.getTotalDeadlineDays(), w.isApprovalRequired(), w.getPublishedAt(),
                w.getPublishedBy(), stages, transitions);
    }

    private WorkflowStageResponse toStageResponse(WorkflowStage s) {
        return new WorkflowStageResponse(s.getId(), s.getCode(), s.getName(), s.getStageType().name(), s.getSequence(),
                s.getParallelGroup(), s.isRequired(), s.getExternalStageId(), s.getInternalStatusLabel(),
                s.getResponsibleRoleCode(), s.getResponsibleDepartmentId(), s.getAssignmentMode().name(),
                s.getDeadlineDays(), s.getExpeditedDeadlineDays(), s.getWorkTypeId(), s.getProducesDocumentType(),
                s.isRequiresResult(), s.isRevisionAllowed(), s.isApprovalRequired(),
                s.getApprovalMode() == null ? null : s.getApprovalMode().name());
    }
}
