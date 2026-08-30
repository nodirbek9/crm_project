package uz.ithunter.crm.casemodule;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import uz.ithunter.crm.casemodule.port.PriceConfirmationChecker;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.application.Application;
import uz.ithunter.crm.application.ApplicationRepository;
import uz.ithunter.crm.application.ApplicationStatus;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.dto.ApplicantTrackingResponse;
import uz.ithunter.crm.casemodule.dto.CaseItemRequest;
import uz.ithunter.crm.casemodule.dto.CaseItemResponse;
import uz.ithunter.crm.casemodule.dto.CaseResponse;
import uz.ithunter.crm.casemodule.dto.CaseSummary;
import uz.ithunter.crm.casemodule.dto.CommentResponse;
import uz.ithunter.crm.casemodule.dto.CreateCommentRequest;
import uz.ithunter.crm.casemodule.dto.PrimaryCheckRequest;
import uz.ithunter.crm.casemodule.dto.RegisterApplicationRequest;
import uz.ithunter.crm.casemodule.dto.StageTimelineItem;
import uz.ithunter.crm.casemodule.engine.DeadlineCalculator;
import uz.ithunter.crm.casemodule.engine.ExternalStageMapper;
import uz.ithunter.crm.casemodule.engine.WorkflowEngine;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.ConflictException;
import uz.ithunter.crm.shared.exception.FieldIssue;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.workflow.ExternalStage;
import uz.ithunter.crm.workflow.ExternalStageRepository;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowRepository;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;
import uz.ithunter.crm.workflow.WorkflowStatus;

/**
 * The application service for Phase 7 (API_SPEC.md 4, WORKFLOW_ENGINE_DESIGN.md 3). Every
 * public method is the single transaction boundary for its use-case; every case-scoped mutation
 * writes audit through {@link AuditWriter}; every case read goes through
 * {@link CaseAccessPolicy}'s two-layer check.
 *
 * <p>No JPA entity ever leaves a public method. {@link CaseMapper} and
 * {@link ApplicantTrackingMapper} produce the closed DTOs the controller serialises.
 */
@Service
public class CaseService {

    /** {@code ck_case_item_quantity} in V5: quantity must be strictly positive. */
    private static final BigDecimal MIN_QUANTITY = new BigDecimal("0.001");

    /**
     * "Only before price confirmation" (API_SPEC.md 4), expressed with what Phase 7 can see. A case
     * that is waiting for payment, running, on signing or closed has a price the applicant has already
     * been told - changing the composition underneath it would silently invalidate a contract.
     */
    private static final Set<CaseStatus> ITEMS_LOCKED_STATUSES = EnumSet.of(
            CaseStatus.WAITING_PAYMENT, CaseStatus.IN_EXECUTION, CaseStatus.FINAL_REVIEW,
            CaseStatus.ON_SIGNING, CaseStatus.COMPLETED, CaseStatus.REJECTED);

    private final ElectronicCaseRepository electronicCaseRepository;
    private final CaseStageRepository caseStageRepository;
    private final CaseItemRepository caseItemRepository;
    private final CaseCommentRepository caseCommentRepository;
    private final PrimaryCheckRepository primaryCheckRepository;
    private final ApplicationRepository applicationRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStageRepository workflowStageRepository;
    private final ExternalStageRepository externalStageRepository;
    private final WorkflowEngine workflowEngine;
    private final DeadlineCalculator deadlineCalculator;
    private final ExternalStageMapper externalStageMapper;
    private final CaseMapper caseMapper;
    private final ApplicantTrackingMapper applicantTrackingMapper;
    private final CaseAccessPolicy caseAccessPolicy;
    private final PrimaryCheckEvaluator primaryCheckEvaluator;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final ObjectProvider<PriceConfirmationChecker> priceConfirmationChecker;

    public CaseService(ElectronicCaseRepository electronicCaseRepository,
            CaseStageRepository caseStageRepository,
            CaseItemRepository caseItemRepository,
            CaseCommentRepository caseCommentRepository,
            PrimaryCheckRepository primaryCheckRepository,
            ApplicationRepository applicationRepository,
            WorkflowRepository workflowRepository,
            WorkflowStageRepository workflowStageRepository,
            ExternalStageRepository externalStageRepository,
            WorkflowEngine workflowEngine,
            DeadlineCalculator deadlineCalculator,
            ExternalStageMapper externalStageMapper,
            CaseMapper caseMapper,
            ApplicantTrackingMapper applicantTrackingMapper,
            CaseAccessPolicy caseAccessPolicy,
            PrimaryCheckEvaluator primaryCheckEvaluator,
            AuditWriter auditWriter,
            ObjectMapper objectMapper,
            EntityManager entityManager,
            ObjectProvider<PriceConfirmationChecker> priceConfirmationChecker) {
        this.electronicCaseRepository = electronicCaseRepository;
        this.caseStageRepository = caseStageRepository;
        this.caseItemRepository = caseItemRepository;
        this.caseCommentRepository = caseCommentRepository;
        this.primaryCheckRepository = primaryCheckRepository;
        this.applicationRepository = applicationRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStageRepository = workflowStageRepository;
        this.externalStageRepository = externalStageRepository;
        this.workflowEngine = workflowEngine;
        this.deadlineCalculator = deadlineCalculator;
        this.externalStageMapper = externalStageMapper;
        this.caseMapper = caseMapper;
        this.applicantTrackingMapper = applicantTrackingMapper;
        this.caseAccessPolicy = caseAccessPolicy;
        this.primaryCheckEvaluator = primaryCheckEvaluator;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.priceConfirmationChecker = priceConfirmationChecker;
    }

    // ---- registration (WORKFLOW_ENGINE_DESIGN.md 3, spec 1.4) ----

    /**
     * Registers an application: creates the case, materialises stages, activates the entry stage,
     * materialises items from {@code formData.items}, and writes the audit trio — all in ONE
     * transaction (I-01, I-02).
     *
     * <p>The caller is a staff member with {@code APPLICATION:EDIT} (spec 1.3), not the applicant.
     */
    @Transactional
    public CaseResponse register(UUID applicationId, RegisterApplicationRequest request,
            CustomUserPrincipal principal) {
        caseAccessPolicy.requireStaff(principal);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION",
                    "Only a SUBMITTED application can be registered, but status is "
                            + application.getStatus());
        }
        if (electronicCaseRepository.existsByApplicationId(applicationId)) {
            throw new ConflictException("ALREADY_REGISTERED",
                    "Application " + applicationId + " already has a case");
        }
        requireRegistrationData(application);

        // Resolve the ACTIVE workflow for this service (spec 5.12, I-02).
        List<Workflow> activeWorkflows = workflowRepository
                .findByServiceIdAndStatusOrderByCodeAsc(application.getServiceId(), WorkflowStatus.ACTIVE);
        if (activeWorkflows.isEmpty()) {
            throw new IllegalStateTransitionException("NO_ACTIVE_WORKFLOW",
                    "No active workflow exists for service " + application.getServiceId());
        }
        Workflow workflow = activeWorkflows.get(0);
        List<WorkflowStage> stageConfigs = workflowStageRepository.findByWorkflowId(workflow.getId());

        // Create the case (spec 1.4).
        Instant now = Instant.now();
        ElectronicCase electronicCase = new ElectronicCase();
        electronicCase.setCaseNumber(generateCaseNumber());
        electronicCase.setApplicationId(applicationId);
        electronicCase.setApplicantId(application.getApplicantId());
        electronicCase.setServiceId(application.getServiceId());
        electronicCase.setWorkflowId(workflow.getId());
        electronicCase.setStatus(CaseStatus.REGISTERED);
        electronicCase.setMainResponsibleDepartmentId(workflow.getMainResponsibleDepartmentId());
        // The overall route deadline (spec 5.8). The mode is null at registration - accounting chooses
        // it in Phase 8 - so this is the TRADITIONAL figure; DeadlineCalculator treats null as
        // TRADITIONAL rather than guessing the shorter one and reporting the case overdue too early.
        electronicCase.setDueAt(deadlineCalculator.caseDueAt(workflow, null, now));
        applyParticipatingDepartments(electronicCase, workflow, stageConfigs);
        electronicCase = electronicCaseRepository.save(electronicCase);

        // Materialise CaseItem rows from formData.items (Phase 5 deferred this to Phase 7).
        materializeItems(electronicCase, application);

        // Materialise one case_stage per workflow_stage, all PENDING (spec 4.4, I-01).
        workflowEngine.materialiseStages(electronicCase);

        // Activate entry stage(s) via the engine (I-02).
        workflowEngine.activateEntryStages(electronicCase);

        // Update the application to REGISTERED.
        application.setStatus(ApplicationStatus.REGISTERED);
        application.setRegisteredAt(now);
        application.setRegisteredById(principal.userId());
        applicationRepository.save(application);

        // Audit: CASE_CREATED carries workflowId/workflowCode in its payload, which implicitly
        // covers ROUTE_ASSIGNED for the initial registration. Phase 11 adds an explicit
        // ROUTE_ASSIGNED event here so the enum value is definitively emitted and the audit
        // sweep can confirm coverage without reading CASE_CREATED's payload fields.
        // ROUTE_CHANGED (separate action) is emitted when a case is re-routed to a different
        // workflow after the primary check (see CaseService.changeWorkflow / re-routing logic).
        auditWriter.write(AuditEvent.forCase(AuditAction.CASE_CREATED, "ElectronicCase",
                electronicCase.getId(), electronicCase.getId(), null,
                Map.of("caseNumber", electronicCase.getCaseNumber(),
                        "applicationId", applicationId.toString(),
                        "workflowId", workflow.getId().toString(),
                        "workflowCode", workflow.getCode(),
                        "workflowVersion", workflow.getVersion()),
                null));
        auditWriter.write(AuditEvent.forCase(AuditAction.ROUTE_ASSIGNED, "ElectronicCase",
                electronicCase.getId(), electronicCase.getId(), null,
                Map.of("workflowId", workflow.getId().toString(),
                        "workflowCode", workflow.getCode()),
                null));
        auditWriter.write(AuditEvent.forCase(AuditAction.CASE_REGISTERED, "ElectronicCase",
                electronicCase.getId(), electronicCase.getId(), null,
                Map.of("registeredBy", principal.userId().toString(),
                        "applicationNumber", application.getNumber()),
                request.note()));

        return caseMapper.toResponse(electronicCase);
    }

    // ---- listing (API_SPEC.md 4: GET /cases) ----

    /**
     * Filtered listing with applicant-scoped repository query (SECURITY_SPEC.md 5). Staff callers
     * pass {@code null} for the applicant scope; applicants are bound into the SQL.
     *
     * <p>The {@code departmentId} filter is not taken at face value. A department-scoped caller
     * (DEPARTMENT_HEAD, SPECIALIST) is pinned to their own department, and asking for another one is a
     * 403 rather than a silently empty page - an empty page reads like "no such cases", which is a
     * different and misleading answer. Org-wide readers keep the parameter as a plain filter.
     */
    @Transactional(readOnly = true)
    public PageResponse<CaseSummary> list(CaseStatus status, UUID serviceId, UUID departmentId,
            ProcessingMode mode, Boolean overdue, String stageCode, String q,
            Pageable pageable, CustomUserPrincipal principal) {
        UUID applicantId = caseAccessPolicy.applicantScopeOf(principal);
        UUID departmentScope = caseAccessPolicy.departmentScopeOf(principal);
        if (departmentScope != null && departmentId != null && !departmentScope.equals(departmentId)) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED",
                    "You may only list cases of your own department");
        }
        UUID effectiveDepartmentId = departmentScope != null ? departmentScope : departmentId;
        String qLike = (q == null || q.isBlank()) ? null : "%" + q.toLowerCase() + "%";
        Page<ElectronicCase> page = electronicCaseRepository.search(
                applicantId, status, serviceId, mode, effectiveDepartmentId, overdue, stageCode, qLike,
                Instant.now(), pageable);
        List<CaseSummary> summaries = caseMapper.toSummaries(page.getContent());
        return new PageResponse<>(summaries, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    // ---- single case (API_SPEC.md 4: GET /cases/{id}) ----

    @Transactional(readOnly = true)
    public CaseResponse get(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        return caseMapper.toResponse(electronicCase);
    }

    // ---- timeline (API_SPEC.md 4: GET /cases/{id}/timeline, staff-only) ----

    @Transactional(readOnly = true)
    public List<StageTimelineItem> timeline(UUID caseId, CustomUserPrincipal principal) {
        caseAccessPolicy.requireStaff(principal);
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        return caseMapper.toTimeline(electronicCase);
    }

    // ---- applicant tracking (API_SPEC.md 4: GET /cases/{id}/tracking) ----

    /**
     * The applicant-facing tracking view (spec 4.19, 15.5–15.7). Uses a projection over six columns
     * rather than the entity so that internal fields cannot leak through serialization (test S-07).
     *
     * <p>Both authorization layers still apply. The endpoint is the applicant's, but a staff client
     * may legitimately open it to see "what does the applicant see right now" - and ADMIN may not,
     * because {@code canViewCase} says so (spec 16.17). So the case is loaded and checked exactly as
     * in {@link #get}, and only then is the narrow projection read.
     */
    @Transactional(readOnly = true)
    public ApplicantTrackingResponse tracking(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);

        UUID applicantId = caseAccessPolicy.applicantScopeOf(principal);
        CaseTrackingProjection projection = electronicCaseRepository
                .findTracking(caseId, applicantId)
                .orElseThrow(() -> new NotFoundException("Case not found"));

        // Resolve external stage from the active internal stages.
        Map<UUID, WorkflowStage> configs = caseMapper.stageConfigs(electronicCase.getWorkflowId());
        List<CaseStage> caseStages = caseStageRepository.findByCaseId(caseId);
        List<WorkflowStage> activeConfigs = caseStages.stream()
                .filter(stage -> stage.getStatus() == CaseStageStatus.ACTIVE)
                .map(stage -> configs.get(stage.getWorkflowStageId()))
                .filter(config -> config != null)
                .toList();
        List<ExternalStage> externalStages = externalStageRepository.findAll();

        ExternalStage externalStage;
        if (!activeConfigs.isEmpty()) {
            externalStage = externalStageMapper.resolveCurrent(activeConfigs, externalStages).orElse(null);
        } else {
            // Completed/returned/rejected: show where the case ended.
            List<WorkflowStage> completedConfigs = caseStages.stream()
                    .filter(stage -> stage.getStatus() == CaseStageStatus.COMPLETED)
                    .map(stage -> configs.get(stage.getWorkflowStageId()))
                    .filter(config -> config != null)
                    .toList();
            externalStage = externalStageMapper.resolveFurthest(completedConfigs, externalStages)
                    .orElse(null);
        }

        // Correction reason from the latest primary check (for RETURNED cases, test I-07).
        String correctionReason = null;
        String correctionRemarks = null;
        if (projection.status() == CaseStatus.RETURNED) {
            PrimaryCheck latestCheck = primaryCheckRepository
                    .findFirstByCaseIdOrderByAttemptNoDesc(caseId).orElse(null);
            if (latestCheck != null) {
                correctionReason = latestCheck.getReason();
                correctionRemarks = extractRemarks(latestCheck.getChecklist());
            }
        }

        return applicantTrackingMapper.toResponse(projection, externalStage,
                correctionReason, correctionRemarks);
    }

    // ---- primary check (API_SPEC.md 4: POST /cases/{id}/primary-check) ----

    /**
     * Records category + decision as two independent values (U-04). Decision-specific side effects:
     * ACCEPTED advances to accounting, RETURNED sets status RETURNED, ROUTE_CHANGED rebinds the
     * workflow, REJECTED/NON_APPLICABILITY_OPINION close the case.
     *
     * <p>API_SPEC.md 4 also requires "assigned task" here. Task assignment is Phase 9's table, so the
     * check that exists today is staff + object-level view; ASSUMPTIONS.md A26 records the gap so it
     * is not mistaken for a finished authorization rule.
     */
    @Transactional
    public CaseResponse performPrimaryCheck(UUID caseId, PrimaryCheckRequest request,
            CustomUserPrincipal principal) {
        caseAccessPolicy.requireStaff(principal);
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);

        if (electronicCase.getStatus() != CaseStatus.PRIMARY_CHECK
                && electronicCase.getStatus() != CaseStatus.REGISTERED) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION",
                    "Primary check requires status PRIMARY_CHECK or REGISTERED, but is "
                            + electronicCase.getStatus());
        }

        // Validate reason is present when required (I-09).
        if (primaryCheckEvaluator.requiresReason(request.decision())
                && (request.reason() == null || request.reason().isBlank())) {
            throw new ValidationException("VALIDATION_FAILED",
                    "reason is required for decision " + request.decision(),
                    List.of(new FieldIssue("reason", "must not be blank for decision "
                            + request.decision())));
        }

        // ROUTE_CHANGED requires newWorkflowId.
        if (request.decision() == PrimaryCheckDecision.ROUTE_CHANGED && request.newWorkflowId() == null) {
            throw new ValidationException("VALIDATION_FAILED",
                    "newWorkflowId is required for ROUTE_CHANGED decision");
        }

        // Determine the next attempt number.
        int attemptNo = primaryCheckRepository.findFirstByCaseIdOrderByAttemptNoDesc(caseId)
                .map(pc -> pc.getAttemptNo() + 1)
                .orElse(1);

        // Create the primary check record (I-06).
        PrimaryCheck check = new PrimaryCheck();
        check.setCaseId(caseId);
        check.setAttemptNo(attemptNo);
        check.setPerformedById(principal.userId());
        check.setPerformedAt(Instant.now());
        check.setCategory(request.category());
        check.setDecision(request.decision());
        check.setReason(request.reason());
        check.setChecklist(request.checklist() == null ? "{}"
                : objectMapper.writeValueAsString(request.checklist()));
        if (request.decision() == PrimaryCheckDecision.ROUTE_CHANGED) {
            check.setNewWorkflowId(request.newWorkflowId());
        }
        primaryCheckRepository.save(check);

        // Mirror onto the case (spec 1.5, 4.6).
        electronicCase.setPrimaryCheckCategory(request.category());
        electronicCase.setPrimaryCheckDecision(request.decision());

        // Audit the check itself. `derivedCategory` is what PrimaryCheckEvaluator grades the same
        // checklist as (spec 1.5): the specialist's own `category` stays authoritative - spec 4.6 gives
        // them the judgment call, and ASSUMPTIONS.md A34 records why the system does not overrule it -
        // but recording both makes a divergence visible to a reviewer instead of invisible.
        Map<String, Object> checkAuditValue = new HashMap<>();
        checkAuditValue.put("attemptNo", attemptNo);
        checkAuditValue.put("category", request.category().name());
        checkAuditValue.put("derivedCategory",
                primaryCheckEvaluator.categoryFrom(request.checklist()).name());
        checkAuditValue.put("decision", request.decision().name());
        if (request.reason() != null) {
            checkAuditValue.put("reason", request.reason());
        }
        auditWriter.write(AuditEvent.forCase(AuditAction.PRIMARY_CHECK_COMPLETED, "PrimaryCheck",
                check.getId(), caseId, null, checkAuditValue, null));
        auditWriter.write(AuditEvent.forCase(AuditAction.CATEGORY_ASSIGNED, "ElectronicCase",
                electronicCase.getId(), caseId, null,
                Map.of("category", request.category().name()), null));
        auditWriter.write(AuditEvent.forCase(AuditAction.PRIMARY_CHECK_DECISION_RECORDED, "ElectronicCase",
                electronicCase.getId(), caseId, null,
                Map.of("decision", request.decision().name()), null));

        // Decision-specific side effects.
        applyPrimaryCheckDecision(electronicCase, request);

        electronicCaseRepository.save(electronicCase);
        return caseMapper.toResponse(electronicCase);
    }

    // ---- case items (API_SPEC.md 4: GET/PUT /cases/{id}/items) ----

    @Transactional(readOnly = true)
    public List<CaseItemResponse> listItems(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        return caseItemRepository.findByCaseIdOrderByLineNoAsc(caseId).stream()
                .map(caseMapper::toItemResponse)
                .toList();
    }

    /**
     * Replaces the whole item composition (spec 4.9). The list is validated here rather than only by
     * {@code @Valid} on the controller parameter: bean validation does not cascade into the elements
     * of a {@code List} request body, so trusting the annotation would let a blank {@code itemName}
     * reach the {@code NOT NULL} column and surface as a 500 instead of a 400.
     *
     * <p>Items are only editable before price confirmation (API_SPEC.md 4). The contract row that
     * would prove confirmation is Phase 8's, so the check that exists today is the case lifecycle -
     * a case already past accounting, rejected or completed does not accept a new composition
     * (ASSUMPTIONS.md A33).
     */
    @Transactional
    public List<CaseItemResponse> updateItems(UUID caseId, List<CaseItemRequest> items,
            CustomUserPrincipal principal) {
        caseAccessPolicy.requireStaff(principal);
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        List<CaseItemRequest> requested = items == null ? List.of() : items;
        validateItems(requested);
        requireItemsEditable(electronicCase);

        caseItemRepository.deleteByCaseId(caseId);
        // The delete must reach the database before the inserts: uq_case_item_line (case_id, line_no)
        // is violated by Hibernate's default insert-before-delete flush order (IMPLEMENTATION_STATUS
        // records the same trap in Phase 6's updateStages).
        entityManager.flush();

        List<CaseItem> created = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            CaseItemRequest item = requested.get(i);
            CaseItem caseItem = new CaseItem();
            caseItem.setCaseId(caseId);
            caseItem.setLineNo(i + 1);
            caseItem.setItemName(item.itemName());
            caseItem.setItemCode(item.itemCode());
            caseItem.setQuantity(item.quantity() != null ? item.quantity() : BigDecimal.ONE);
            caseItem.setUnit(item.unit() != null ? item.unit() : "PCS");
            caseItem.setObjectAddress(item.objectAddress());
            caseItem.setAttributes(item.attributes() == null ? "{}"
                    : objectMapper.writeValueAsString(item.attributes()));
            created.add(caseItem);
        }
        return caseItemRepository.saveAll(created).stream()
                .map(caseMapper::toItemResponse)
                .toList();
    }

    // ---- comments (API_SPEC.md 4: POST/GET /cases/{id}/comments, staff-only) ----

    /**
     * Internal-only comments (spec 13.5.1, 17.8). {@code visibility} is not taken from the request:
     * every comment written through this endpoint is INTERNAL, so there is no field a client could set
     * to make one visible to the applicant.
     *
     * <p>Deliberately unaudited - see ASSUMPTIONS.md A32. {@code AuditAction} mirrors
     * {@code ck_audit_action} value for value, so inventing a {@code COMMENT_ADDED} constant here would
     * be a row the database rejects at insert time; the honest options were "no audit" or "a migration
     * outside this phase's scope", and the audit trail's integrity guarantee is not worth breaking for
     * a comment.
     */
    @Transactional
    public CommentResponse addComment(UUID caseId, CreateCommentRequest request,
            CustomUserPrincipal principal) {
        caseAccessPolicy.requireStaff(principal);
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);

        CaseComment comment = new CaseComment();
        comment.setCaseId(caseId);
        comment.setAuthorId(principal.userId());
        comment.setAuthorDepartmentId(principal.departmentId());
        comment.setBody(request.body());
        comment.setDocumentVersionId(request.documentVersionId());
        comment.setVisibility(CommentVisibility.INTERNAL);
        comment = caseCommentRepository.save(comment);
        return caseMapper.toCommentResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(UUID caseId, CustomUserPrincipal principal) {
        caseAccessPolicy.requireStaff(principal);
        ElectronicCase electronicCase = findScopedOrThrow(caseId, principal);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        return caseCommentRepository.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
                .map(caseMapper::toCommentResponse)
                .toList();
    }

    // ---- private helpers ----

    private ElectronicCase findScopedOrThrow(UUID caseId, CustomUserPrincipal principal) {
        UUID applicantId = caseAccessPolicy.applicantScopeOf(principal);
        return electronicCaseRepository.findScopedById(caseId, applicantId)
                .orElseThrow(() -> new NotFoundException("Case not found"));
    }

    private String generateCaseNumber() {
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        long sequence = electronicCaseRepository.count() + 1;
        return "CASE-" + year + "-" + String.format("%06d", sequence);
    }

    /**
     * {@code 422 REQUIRED_DATA_MISSING} of API_SPEC.md 3 (spec 15.3).
     *
     * <p>The spec phrases this as "route-mandatory fields or documents are absent", but nothing in the
     * schema declares which fields a route mandates: {@code workflow_stage} has no required-field or
     * required-document configuration, and Phase 10 owns documents. So the check is the part that IS
     * expressible today - an application that carries neither a single form field nor a single item
     * cannot be turned into a case - and ASSUMPTIONS.md A31 records the narrowing. Widening it later is
     * a change to this one method, not to the endpoint or the transaction.
     *
     * <p>The {@code items} key is excluded from the "any field" test because Phase 5's
     * {@code mergeFormData} always writes it, empty list included: counting it as data would make this
     * guard unreachable through the API.
     */
    private void requireRegistrationData(Application application) {
        Map<String, Object> formData = readJsonObject(application.getFormData());
        boolean anyField = formData.keySet().stream().anyMatch(key -> !"items".equals(key));
        boolean anyItem = formData.get("items") instanceof List<?> items && !items.isEmpty();
        if (!anyField && !anyItem) {
            throw new IllegalStateTransitionException("REQUIRED_DATA_MISSING",
                    "Application " + application.getId() + " carries no form data to register");
        }
    }

    /**
     * The departments the route will actually involve (spec 4.13): every stage's responsible
     * department except the main one, which has its own column.
     *
     * <p>This is what makes department-scoped access work from the moment of registration rather than
     * from the moment a task is assigned - {@link CaseAccessPolicy} reads exactly this set for
     * DEPARTMENT_HEAD and SPECIALIST (SECURITY_SPEC.md 5), and Phase 9's assignment does not yet
     * exist to populate it.
     */
    private Set<UUID> participatingDepartments(Workflow workflow, List<WorkflowStage> stageConfigs) {
        Set<UUID> departments = new LinkedHashSet<>();
        for (WorkflowStage config : stageConfigs) {
            UUID departmentId = config.getResponsibleDepartmentId();
            if (departmentId != null && !departmentId.equals(workflow.getMainResponsibleDepartmentId())) {
                departments.add(departmentId);
            }
        }
        return departments;
    }

    /**
     * Mutates the existing {@code @ElementCollection} instead of replacing it. Handing Hibernate a
     * brand-new {@code Set} for a collection it is already managing is the classic way to get a
     * dereferenced-collection failure at flush time.
     */
    private void applyParticipatingDepartments(ElectronicCase electronicCase, Workflow workflow,
            List<WorkflowStage> stageConfigs) {
        Set<UUID> departments = participatingDepartments(workflow, stageConfigs);
        electronicCase.getParticipatingDepartmentIds().clear();
        electronicCase.getParticipatingDepartmentIds().addAll(departments);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        return parsed == null ? Map.of() : parsed;
    }

    /** Field-level validation of the PUT body, reported as API_SPEC.md 9's {@code {field, issue}} list. */
    private void validateItems(List<CaseItemRequest> items) {
        List<FieldIssue> issues = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CaseItemRequest item = items.get(i);
            String path = "items[" + i + "]";
            if (item == null) {
                issues.add(new FieldIssue(path, "must not be null"));
                continue;
            }
            if (item.itemName() == null || item.itemName().isBlank()) {
                issues.add(new FieldIssue(path + ".itemName", "must not be blank"));
            } else if (item.itemName().length() > 255) {
                issues.add(new FieldIssue(path + ".itemName", "must be at most 255 characters"));
            }
            if (item.itemCode() != null && item.itemCode().length() > 60) {
                issues.add(new FieldIssue(path + ".itemCode", "must be at most 60 characters"));
            }
            if (item.quantity() == null) {
                issues.add(new FieldIssue(path + ".quantity", "must not be null"));
            } else if (item.quantity().compareTo(MIN_QUANTITY) < 0) {
                issues.add(new FieldIssue(path + ".quantity", "must be at least " + MIN_QUANTITY));
            }
            if (item.unit() == null || item.unit().isBlank()) {
                issues.add(new FieldIssue(path + ".unit", "must not be blank"));
            } else if (item.unit().length() > 20) {
                issues.add(new FieldIssue(path + ".unit", "must be at most 20 characters"));
            }
            if (item.objectAddress() != null && item.objectAddress().length() > 500) {
                issues.add(new FieldIssue(path + ".objectAddress", "must be at most 500 characters"));
            }
        }
        if (!issues.isEmpty()) {
            throw new ValidationException("VALIDATION_FAILED", "Request validation failed", issues);
        }
    }

    /**
     * ASSUMPTIONS.md A33: the {@code CaseStatus} proxy stays as a first, always-available guard,
     * but once Phase 8's real event exists - a {@code CONFIRMED} price calculation - that is the
     * actual rule from API_SPEC.md 4 ("only before price confirmation"), and it fires strictly
     * earlier than any status in {@link #ITEMS_LOCKED_STATUSES} (a case can be confirmed while
     * still {@code IN_ACCOUNTING}, before payment/contract even lock the status).
     */
    private void requireItemsEditable(ElectronicCase electronicCase) {
        if (ITEMS_LOCKED_STATUSES.contains(electronicCase.getStatus())) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION",
                    "Case items can no longer be changed in status " + electronicCase.getStatus());
        }
        PriceConfirmationChecker checker = priceConfirmationChecker.getIfAvailable();
        if (checker != null && checker.isPriceConfirmed(electronicCase.getId())) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION",
                    "Case items can no longer be changed once the price is confirmed");
        }
    }

    /**
     * Materialises {@code CaseItem} rows from the application's {@code formData.items} JSON.
     * Phase 5 stored the submitted item composition under this key; this is where it finally becomes
     * queryable table rows (FINAL_DOMAIN_MODEL.md 4.4).
     */
    @SuppressWarnings("unchecked")
    private void materializeItems(ElectronicCase electronicCase, Application application) {
        Map<String, Object> formData = readJsonObject(application.getFormData());
        Object itemsObj = formData.get("items");
        if (!(itemsObj instanceof List<?> itemsList) || itemsList.isEmpty()) {
            return;
        }
        List<CaseItem> items = new ArrayList<>();
        int lineNo = 1;
        for (Object itemObj : itemsList) {
            if (!(itemObj instanceof Map<?, ?> itemMap)) {
                continue;
            }
            CaseItem caseItem = new CaseItem();
            caseItem.setCaseId(electronicCase.getId());
            caseItem.setLineNo(lineNo++);
            caseItem.setItemName(stringOrDefault(itemMap.get("name"), "Item " + (lineNo - 1)));
            caseItem.setItemCode(stringOrNull(itemMap.get("code")));
            caseItem.setQuantity(decimalOrDefault(itemMap.get("quantity"), BigDecimal.ONE));
            caseItem.setUnit(stringOrDefault(itemMap.get("unit"), "PCS"));
            caseItem.setObjectAddress(stringOrNull(itemMap.get("objectAddress")));
            // Everything else goes into the attributes bag.
            Map<String, Object> attrs = new HashMap<>((Map<String, Object>) itemMap);
            attrs.remove("name");
            attrs.remove("code");
            attrs.remove("quantity");
            attrs.remove("unit");
            attrs.remove("objectAddress");
            caseItem.setAttributes(attrs.isEmpty() ? "{}" : objectMapper.writeValueAsString(attrs));
            items.add(caseItem);
        }
        caseItemRepository.saveAll(items);
    }

    private void applyPrimaryCheckDecision(ElectronicCase electronicCase, PrimaryCheckRequest request) {
        switch (request.decision()) {
            case ACCEPTED -> {
                // Complete the PRIMARY_CHECK stage and let the engine advance to ACCOUNTING.
                CaseStage primaryCheckStage = findActiveStageByType(electronicCase,
                        uz.ithunter.crm.workflow.StageType.PRIMARY_CHECK);
                if (primaryCheckStage != null) {
                    workflowEngine.completeStage(electronicCase.getId(),
                            primaryCheckStage.getWorkflowStageId());
                }
            }
            case RETURNED_TO_APPLICANT -> {
                electronicCase.setStatus(CaseStatus.RETURNED);
                auditWriter.write(AuditEvent.forCase(AuditAction.CASE_RETURNED_TO_APPLICANT,
                        "ElectronicCase", electronicCase.getId(), electronicCase.getId(),
                        null, Map.of("reason", request.reason()), null));
            }
            case ROUTE_CHANGED -> {
                // Validate the new workflow exists and is ACTIVE (I-08).
                Workflow newWorkflow = workflowRepository.findById(request.newWorkflowId())
                        .orElseThrow(() -> new NotFoundException("New workflow not found"));
                if (newWorkflow.getStatus() != WorkflowStatus.ACTIVE) {
                    throw new IllegalStateTransitionException("WORKFLOW_NOT_ACTIVE",
                            "Target workflow must be ACTIVE");
                }
                if (newWorkflow.getId().equals(electronicCase.getWorkflowId())) {
                    throw new ValidationException("VALIDATION_FAILED",
                            "newWorkflowId must differ from the current workflow");
                }
                Map<String, Object> oldRoute = Map.of(
                        "workflowId", electronicCase.getWorkflowId().toString());
                electronicCase.setWorkflowId(newWorkflow.getId());
                electronicCase.setMainResponsibleDepartmentId(
                        newWorkflow.getMainResponsibleDepartmentId());
                electronicCase.setCurrentStageId(null);
                List<WorkflowStage> newConfigs = workflowStageRepository
                        .findByWorkflowId(newWorkflow.getId());
                applyParticipatingDepartments(electronicCase, newWorkflow, newConfigs);
                electronicCase.setDueAt(deadlineCalculator.caseDueAt(newWorkflow,
                        electronicCase.getProcessingMode(), Instant.now()));
                // The abandoned route's rows go before the new ones are materialised.
                closeOpenStages(electronicCase, true);
                electronicCaseRepository.save(electronicCase);
                workflowEngine.materialiseStages(electronicCase);
                workflowEngine.activateEntryStages(electronicCase);
                auditWriter.write(AuditEvent.forCase(AuditAction.ROUTE_CHANGED, "ElectronicCase",
                        electronicCase.getId(), electronicCase.getId(), oldRoute,
                        Map.of("workflowId", newWorkflow.getId().toString(),
                                "workflowCode", newWorkflow.getCode()),
                        request.reason()));
            }
            case REJECTED, NON_APPLICABILITY_OPINION -> {
                electronicCase.setStatus(CaseStatus.REJECTED);
                electronicCase.setRejectedAt(Instant.now());
                electronicCase.setRejectionReason(request.reason());
                // No stage may stay open on a closed case, or it keeps showing up as work in
                // progress and starts reporting itself overdue.
                closeOpenStages(electronicCase, false);
                electronicCase.setCurrentStageId(null);
                auditWriter.write(AuditEvent.forCase(AuditAction.CASE_REJECTED, "ElectronicCase",
                        electronicCase.getId(), electronicCase.getId(), null,
                        Map.of("decision", request.decision().name(),
                                "reason", request.reason()),
                        request.reason()));
            }
        }
    }

    /**
     * Closes every stage that is still open on the case.
     *
     * <p>{@code CANCELLED} is only available to stages that were actually reached:
     * {@code ck_case_stage_activated} (V5) allows a NULL {@code activated_at} for {@code PENDING} rows
     * only, so flipping an unreached row to CANCELLED would be rejected by the database. Unreached rows
     * therefore either stay PENDING - on a rejected case, where the route is simply over and the
     * timeline may as well still show what was never reached - or, when {@code discardUnreached} is set
     * because the route itself was replaced, are deleted: they describe a workflow this case no longer
     * runs on, and they carry no history to preserve (never activated, never audited).
     */
    private void closeOpenStages(ElectronicCase electronicCase, boolean discardUnreached) {
        List<CaseStage> cancelled = new ArrayList<>();
        List<CaseStage> discarded = new ArrayList<>();
        for (CaseStage stage : caseStageRepository.findByCaseId(electronicCase.getId())) {
            if (stage.getStatus() == CaseStageStatus.COMPLETED
                    || stage.getStatus() == CaseStageStatus.CANCELLED) {
                continue;
            }
            if (stage.getActivatedAt() == null) {
                if (discardUnreached) {
                    discarded.add(stage);
                }
                continue;
            }
            stage.setStatus(CaseStageStatus.CANCELLED);
            cancelled.add(stage);
        }
        caseStageRepository.saveAll(cancelled);
        if (!discarded.isEmpty()) {
            caseStageRepository.deleteAll(discarded);
            // uq_case_stage is per (case, workflow stage), so the deletes must land before
            // materialiseStages inserts - Hibernate would otherwise flush inserts first.
            entityManager.flush();
        }
    }

    private CaseStage findActiveStageByType(ElectronicCase electronicCase,
            uz.ithunter.crm.workflow.StageType stageType) {
        List<CaseStage> activeStages = caseStageRepository
                .findByCaseIdAndStatus(electronicCase.getId(), CaseStageStatus.ACTIVE);
        for (CaseStage stage : activeStages) {
            WorkflowStage config = workflowStageRepository.findById(stage.getWorkflowStageId())
                    .orElse(null);
            if (config != null && config.getStageType() == stageType) {
                return stage;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractRemarks(String checklistJson) {
        if (checklistJson == null || checklistJson.isBlank()) {
            return null;
        }
        Map<String, Object> checklist = objectMapper.readValue(checklistJson, Map.class);
        Object remarks = checklist.get(PrimaryCheckEvaluator.KEY_REMARKS);
        return remarks instanceof String s ? s : null;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        return value instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    private static String stringOrNull(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private static BigDecimal decimalOrDefault(Object value, BigDecimal defaultValue) {
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
