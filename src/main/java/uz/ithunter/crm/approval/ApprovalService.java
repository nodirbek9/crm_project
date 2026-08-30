package uz.ithunter.crm.approval;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.approval.dto.ApprovalDecisionRequest;
import uz.ithunter.crm.approval.dto.ApprovalRoundResponse;
import uz.ithunter.crm.approval.dto.ApprovalTaskResponse;
import uz.ithunter.crm.approval.dto.ApprovalTaskSummary;
import uz.ithunter.crm.approval.dto.StartApprovalRequest;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.CaseAccessPolicy;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageRepository;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.engine.WorkflowEngine;
import uz.ithunter.crm.document.Document;
import uz.ithunter.crm.document.DocumentRepository;
import uz.ithunter.crm.document.DocumentStatus;
import uz.ithunter.crm.document.DocumentVersion;
import uz.ithunter.crm.document.DocumentVersionRepository;
import uz.ithunter.crm.document.DocumentVersionStatus;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.ConflictException;
import uz.ithunter.crm.shared.exception.FieldIssue;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.WorkflowStageRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ApprovalService {
    private final ApprovalRoundRepository roundRepository;
    private final ApprovalTaskRepository taskRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final CaseAccessPolicy caseAccessPolicy;
    private final ApprovalMapper mapper;
    private final AuditWriter auditWriter;
    private final WorkflowEngine workflowEngine;
    private final CaseStageRepository caseStageRepository;
    private final WorkflowStageRepository workflowStageRepository;

    public ApprovalService(ApprovalRoundRepository roundRepository, ApprovalTaskRepository taskRepository,
                           DocumentVersionRepository versionRepository, DocumentRepository documentRepository,
                           CaseAccessPolicy caseAccessPolicy, ApprovalMapper mapper, AuditWriter auditWriter,
                           WorkflowEngine workflowEngine, CaseStageRepository caseStageRepository,
                           WorkflowStageRepository workflowStageRepository) {
        this.roundRepository = roundRepository;
        this.taskRepository = taskRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.caseAccessPolicy = caseAccessPolicy;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
        this.workflowEngine = workflowEngine;
        this.caseStageRepository = caseStageRepository;
        this.workflowStageRepository = workflowStageRepository;
    }

    @Transactional
    public ApprovalRoundResponse startRound(UUID documentId, int versionNo, StartApprovalRequest request, CustomUserPrincipal principal) {
        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNo(documentId, versionNo)
                .orElseThrow(() -> new NotFoundException("VERSION_NOT_FOUND", "Document version not found"));
        caseAccessPolicy.requireCanView(principal, version.getDocument().getElectronicCase());

        if (roundRepository.findByDocumentVersionIdAndStatus(version.getId(), ApprovalRoundStatus.IN_PROGRESS).isPresent()) {
            throw new ConflictException("VERSION_ALREADY_UNDER_APPROVAL", "Version already has an open approval round");
        }

        List<ApprovalRound> existingRounds = roundRepository.findByDocumentVersionId(version.getId());
        int roundNo = existingRounds.size() + 1;

        ApprovalRound round = new ApprovalRound();
        round.setDocumentVersion(version);
        round.setElectronicCase(version.getDocument().getElectronicCase());
        round.setMode(request.getMode());
        round.setRoundNo(roundNo);
        round.setInitiatedById(principal.userId());
        final ApprovalRound savedRound = roundRepository.save(round);

        List<ApprovalTask> tasks = request.getParticipants().stream().map(p -> {
            ApprovalTask task = new ApprovalTask();
            task.setApprovalRound(savedRound);
            task.setParticipantKind(p.getKind());
            task.setParticipantUserId(p.getUserId());
            task.setParticipantDepartmentId(p.getDepartmentId());
            task.setRequired(p.isRequired());
            task.setSequenceNo(p.getSequenceNo());
            return task;
        }).toList();
        taskRepository.saveAll(tasks);

        version.setStatus(DocumentVersionStatus.UNDER_ENDORSEMENT);
        versionRepository.save(version);
        
        Document doc = version.getDocument();
        doc.setStatus(DocumentStatus.UNDER_ENDORSEMENT);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        auditWriter.write(AuditEvent.forCase(
                AuditAction.APPROVAL_ROUND_STARTED,
                "ApprovalRound",
                round.getId(),
                round.getElectronicCase().getId(),
                null,
                Map.of("mode", round.getMode().name(), "roundNo", roundNo, "participantCount", tasks.size()),
                null
        ));

        // Phase 11 gap-fix: APPROVAL_SENT was never emitted (confirmed by grep before this phase).
        // Per spec 13.3 each participant notification is one event; we emit a single summary row
        // ("sent to N participants") rather than N rows to keep the audit log compact.
        // A future phase can switch to per-participant rows without a schema change.
        auditWriter.write(AuditEvent.forCase(
                AuditAction.APPROVAL_SENT,
                "ApprovalRound",
                savedRound.getId(),
                savedRound.getElectronicCase().getId(),
                null,
                Map.of("participantCount", tasks.size()),
                null
        ));

        return mapper.toRoundResponse(savedRound, tasks);
    }

    @Transactional(readOnly = true)
    public ApprovalRoundResponse getRound(UUID roundId, CustomUserPrincipal principal) {
        ApprovalRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new NotFoundException("ROUND_NOT_FOUND", "Approval round not found"));
        caseAccessPolicy.requireCanView(principal, round.getElectronicCase());

        List<ApprovalTask> tasks = taskRepository.findByApprovalRoundId(roundId);
        return mapper.toRoundResponse(round, tasks);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalTaskSummary> listMyApprovals(CustomUserPrincipal principal, Pageable pageable) {
        return taskRepository.findByParticipantUserId(principal.userId(), pageable)
                .map(mapper::toTaskSummary);
    }

    @Transactional
    public ApprovalTaskResponse approve(UUID taskId, ApprovalDecisionRequest request, CustomUserPrincipal principal) {
        ApprovalTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Approval task not found"));
        
        requireTaskAssignee(task, principal);

        if (task.getStatus() != ApprovalTaskStatus.SENT && task.getStatus() != ApprovalTaskStatus.IN_REVIEW) {
            throw new IllegalStateTransitionException("INVALID_STATE", "Task is not in a decidable state");
        }

        task.setStatus(ApprovalTaskStatus.APPROVED);
        task.setComment(request.getComment());
        task.setDecidedById(principal.userId());
        task.setDecidedAt(Instant.now());
        task = taskRepository.save(task);

        auditWriter.write(AuditEvent.forCase(
                AuditAction.APPROVAL_APPROVED,
                "ApprovalTask",
                task.getId(),
                task.getApprovalRound().getElectronicCase().getId(),
                null, null, null
        ));

        checkRoundCompletion(task.getApprovalRound(), principal);

        return mapper.toTaskResponse(task);
    }

    @Transactional
    public ApprovalTaskResponse reject(UUID taskId, ApprovalDecisionRequest request, CustomUserPrincipal principal) {
        if (request.getComment() == null || request.getComment().trim().isEmpty()) {
            // API_SPEC.md 7 names this its own top-level code, not the generic VALIDATION_FAILED
            // every other phase uses for a plain missing-field 400.
            throw new ValidationException("APPROVAL_COMMENT_REQUIRED", "Comment is required for rejection",
                    List.of(new FieldIssue("comment", "must not be blank when rejecting")));
        }

        ApprovalTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Approval task not found"));
        
        requireTaskAssignee(task, principal);

        if (task.getStatus() != ApprovalTaskStatus.SENT && task.getStatus() != ApprovalTaskStatus.IN_REVIEW) {
            throw new IllegalStateTransitionException("INVALID_STATE", "Task is not in a decidable state");
        }

        task.setStatus(ApprovalTaskStatus.REJECTED);
        task.setComment(request.getComment());
        task.setDecidedById(principal.userId());
        task.setDecidedAt(Instant.now());
        task = taskRepository.save(task);

        auditWriter.write(AuditEvent.forCase(
                AuditAction.APPROVAL_REJECTED,
                "ApprovalTask",
                task.getId(),
                task.getApprovalRound().getElectronicCase().getId(),
                null, null, request.getComment()
        ));

        if (task.isRequired()) {
            ApprovalRound round = task.getApprovalRound();
            round.setStatus(ApprovalRoundStatus.COMPLETED_REJECTED);
            round.setCompletedAt(Instant.now());
            roundRepository.save(round);

            // The round closes the instant one required participant rejects (spec 13.4's
            // "не одобрено" ends the round, it does not wait for the others) - every other task
            // still SENT/IN_REVIEW never gets a decision and must not be left looking like an open
            // question forever.
            UUID rejectedTaskId = task.getId();
            List<ApprovalTask> otherOpenTasks = taskRepository.findByApprovalRoundId(round.getId()).stream()
                    .filter(t -> !t.getId().equals(rejectedTaskId))
                    .filter(t -> t.getStatus() == ApprovalTaskStatus.SENT || t.getStatus() == ApprovalTaskStatus.IN_REVIEW)
                    .toList();
            Instant skippedAt = Instant.now();
            for (ApprovalTask other : otherOpenTasks) {
                other.setStatus(ApprovalTaskStatus.SKIPPED);
                // ck_approval_task_decided requires decided_by_id/decided_at for any status other
                // than SENT/IN_REVIEW - attributed to the rejection that closed the round, since
                // nobody individually decided these.
                other.setDecidedById(principal.userId());
                other.setDecidedAt(skippedAt);
            }
            taskRepository.saveAll(otherOpenTasks);

            DocumentVersion version = round.getDocumentVersion();
            version.setStatus(DocumentVersionStatus.REJECTED);
            versionRepository.save(version);
            
            Document doc = version.getDocument();
            doc.setStatus(DocumentStatus.RETURNED_FOR_REVISION);
            doc.setUpdatedAt(Instant.now());
            documentRepository.save(doc);

            auditWriter.write(AuditEvent.forCase(
                    AuditAction.APPROVAL_ROUND_COMPLETED,
                    "ApprovalRound",
                    round.getId(),
                    round.getElectronicCase().getId(),
                    null, null, null
            ));
        }

        return mapper.toTaskResponse(task);
    }

    private void checkRoundCompletion(ApprovalRound round, CustomUserPrincipal principal) {
        if (round.getStatus() != ApprovalRoundStatus.IN_PROGRESS) return;

        List<ApprovalTask> tasks = taskRepository.findByApprovalRoundId(round.getId());
        boolean allRequiredApproved = tasks.stream()
                .filter(ApprovalTask::isRequired)
                .allMatch(t -> t.getStatus() == ApprovalTaskStatus.APPROVED);

        if (allRequiredApproved) {
            round.setStatus(ApprovalRoundStatus.COMPLETED_APPROVED);
            round.setCompletedAt(Instant.now());
            roundRepository.save(round);

            DocumentVersion version = round.getDocumentVersion();
            version.setStatus(DocumentVersionStatus.ENDORSED);
            versionRepository.save(version);
            
            Document doc = version.getDocument();
            doc.setStatus(DocumentStatus.ENDORSED);
            doc.setUpdatedAt(Instant.now());
            documentRepository.save(doc);

            auditWriter.write(AuditEvent.forCase(
                    AuditAction.APPROVAL_ROUND_COMPLETED,
                    "ApprovalRound",
                    round.getId(),
                    round.getElectronicCase().getId(),
                    null, null, null
            ));

            caseStageRepository.findByCaseId(round.getElectronicCase().getId()).stream()
                    .filter(stage -> stage.getStatus() == CaseStageStatus.ACTIVE)
                    .filter(stage -> workflowStageRepository.findById(stage.getWorkflowStageId())
                            .map(ws -> ws.getStageType() == StageType.ENDORSEMENT)
                            .orElse(false))
                    .findFirst()
                    .ifPresent(stage -> workflowEngine.completeStage(round.getElectronicCase().getId(), stage.getWorkflowStageId()));
        }
    }

    /**
     * All four {@link ParticipantKind} values must be handled explicitly and each branch must
     * throw on a mismatch - an {@code if/else} chain with no matching branch falls through and
     * lets ANY authenticated caller act on the task, which is exactly the fail-open bug this
     * method exists to prevent.
     */
    private void requireTaskAssignee(ApprovalTask task, CustomUserPrincipal principal) {
        switch (task.getParticipantKind()) {
            case USER, APPLICANT -> {
                if (!principal.userId().equals(task.getParticipantUserId())) {
                    throw new AccessDeniedDomainException("NOT_TASK_ASSIGNEE",
                            "You are not assigned to this approval task");
                }
            }
            case DEPARTMENT -> {
                if (task.getParticipantDepartmentId() == null
                        || !task.getParticipantDepartmentId().equals(principal.departmentId())) {
                    throw new AccessDeniedDomainException("NOT_TASK_ASSIGNEE",
                            "You are not in the department assigned to this approval task");
                }
            }
            case ACCOUNTING -> {
                if (!caseAccessPolicy.rolesOf(principal).contains(RoleCode.ACCOUNTANT)) {
                    throw new AccessDeniedDomainException("NOT_TASK_ASSIGNEE",
                            "Only accounting may decide this approval task");
                }
            }
        }
    }
}
