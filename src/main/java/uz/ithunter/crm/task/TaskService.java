package uz.ithunter.crm.task;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.CaseAccessPolicy;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.casemodule.engine.WorkflowEngine;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.ConflictException;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.task.dto.AssignTaskRequest;
import uz.ithunter.crm.task.dto.CompleteTaskRequest;
import uz.ithunter.crm.task.dto.ReturnTaskRequest;
import uz.ithunter.crm.task.dto.SubmitTaskResultRequest;
import uz.ithunter.crm.task.dto.TaskResponse;
import uz.ithunter.crm.task.dto.TaskResultResponse;
import uz.ithunter.crm.task.dto.TaskSummary;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskResultRepository taskResultRepository;
    private final WorkflowEngine workflowEngine;
    private final WorkflowStageRepository workflowStageRepository;
    private final ElectronicCaseRepository caseRepository;
    private final AuditWriter auditWriter;
    private final CaseAccessPolicy caseAccessPolicy;
    private final TaskMapper taskMapper;
    private final EntityManager entityManager;

    public TaskService(TaskRepository taskRepository,
            TaskResultRepository taskResultRepository,
            WorkflowEngine workflowEngine,
            WorkflowStageRepository workflowStageRepository,
            ElectronicCaseRepository caseRepository,
            AuditWriter auditWriter,
            CaseAccessPolicy caseAccessPolicy,
            TaskMapper taskMapper,
            EntityManager entityManager) {
        this.taskRepository = taskRepository;
        this.taskResultRepository = taskResultRepository;
        this.workflowEngine = workflowEngine;
        this.workflowStageRepository = workflowStageRepository;
        this.caseRepository = caseRepository;
        this.auditWriter = auditWriter;
        this.caseAccessPolicy = caseAccessPolicy;
        this.taskMapper = taskMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummary> list(TaskStatus status, UUID caseId, UUID departmentId, UUID assigneeId, Boolean overdue, Pageable pageable, CustomUserPrincipal principal) {
        UUID scopeDeptId = caseAccessPolicy.departmentScopeOf(principal);
        // If scoped, enforce it unless they search within their own department
        UUID effectiveDeptId = (scopeDeptId != null) ? scopeDeptId : departmentId;
        
        var page = taskRepository.search(status, caseId, effectiveDeptId, assigneeId, overdue, pageable);
        return PageResponse.of(page, taskMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummary> listMyTasks(Pageable pageable, CustomUserPrincipal principal) {
        var page = taskRepository.findByAssignedUserId(principal.userId(), pageable);
        return PageResponse.of(page, taskMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID id, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        ElectronicCase electronicCase = requireCase(task.getCaseId());
        caseAccessPolicy.requireCanView(principal, electronicCase);
        return taskMapper.toResponse(task);
    }

    public TaskResponse assign(UUID id, AssignTaskRequest request, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        requireTaskDepartmentHead(task, principal);

        task.setAssignedUserId(request.userId());
        task.setAssignedById(principal.userId());
        task.setAssignedAt(Instant.now());
        task.setStatus(TaskStatus.ASSIGNED);

        taskRepository.save(task);
        auditWriter.write(AuditEvent.forCase(AuditAction.TASK_ASSIGNED, "Task", task.getId(), task.getCaseId(), null, Map.of("assignedUserId", request.userId().toString()), null));
        return taskMapper.toResponse(task);
    }

    public TaskResponse reassign(UUID id, AssignTaskRequest request, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        requireTaskDepartmentHead(task, principal);

        if (request.reason() == null || request.reason().isBlank()) {
            throw new ValidationException("REASON_REQUIRED", "Reassignment requires a reason");
        }

        task.setAssignedUserId(request.userId());
        task.setAssignedById(principal.userId());
        task.setAssignedAt(Instant.now());
        // A CREATED task becomes ASSIGNED; a task already IN_PROGRESS/SUBMITTED_FOR_REVIEW/
        // RETURNED_FOR_REVISION keeps its own status - reassignment changes who is doing the work,
        // not how far along it is.
        if (task.getStatus() == TaskStatus.CREATED) {
            task.setStatus(TaskStatus.ASSIGNED);
        }

        taskRepository.save(task);
        auditWriter.write(AuditEvent.forCase(AuditAction.TASK_REASSIGNED, "Task", task.getId(), task.getCaseId(), null, Map.of("assignedUserId", request.userId().toString(), "reason", request.reason()), null));
        return taskMapper.toResponse(task);
    }

    public TaskResponse start(UUID id, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        requireTaskAssignee(task, principal);

        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateTransitionException("INVALID_STATUS", "Cannot start a completed or cancelled task");
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        if (task.getStartedAt() == null) {
            task.setStartedAt(Instant.now());
        }

        taskRepository.save(task);
        auditWriter.write(AuditEvent.forCase(AuditAction.TASK_STARTED, "Task", task.getId(), task.getCaseId(), null, null, null));
        return taskMapper.toResponse(task);
    }

    public TaskResultResponse submitResult(UUID id, SubmitTaskResultRequest request, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        requireTaskAssignee(task, principal);

        TaskResult liveResult = taskResultRepository.findByTaskIdAndStatusIn(task.getId(), List.of(TaskResultStatus.SUBMITTED, TaskResultStatus.APPROVED)).orElse(null);
        
        if (liveResult != null && liveResult.getStatus() == TaskResultStatus.APPROVED) {
            throw new ConflictException("RESULT_ALREADY_APPROVED", "Cannot overwrite an approved result. Use return to revision.");
        }

        int nextVersion = taskResultRepository.countByTaskId(task.getId()) + 1;
        if (nextVersion > 1 && (request.revisionReason() == null || request.revisionReason().isBlank())) {
            throw new ValidationException("REVISION_REASON_REQUIRED", "A reason is required when superseding a result.");
        }

        if (liveResult != null) {
            liveResult.setStatus(TaskResultStatus.SUPERSEDED);
            taskResultRepository.save(liveResult);
            // uq_task_result_live is a partial unique index on task_id WHERE status IN
            // (SUBMITTED, APPROVED) - the supersede UPDATE must reach the database before the new
            // row is INSERTed as SUBMITTED, or Hibernate's default insert-before-update flush order
            // would momentarily have two live rows and the insert would violate the index (same trap
            // as CaseService#applyPrimaryCheckDecision's ROUTE_CHANGED branch and #updateItems).
            entityManager.flush();
        }

        TaskResult newResult = new TaskResult();
        newResult.setTaskId(task.getId());
        newResult.setVersionNo(nextVersion);
        newResult.setPayload(request.payload());
        newResult.setSummary(request.summary());
        newResult.setStatus(TaskResultStatus.SUBMITTED);
        newResult.setAuthorId(principal.userId());
        if (liveResult != null) {
            newResult.setSupersedesId(liveResult.getId());
            newResult.setRevisionReason(request.revisionReason());
        }

        taskResultRepository.save(newResult);
        
        task.setStatus(TaskStatus.SUBMITTED_FOR_REVIEW);
        taskRepository.save(task);
        
        auditWriter.write(AuditEvent.forCase(AuditAction.RESULT_VERSION_CREATED, "TaskResult", newResult.getId(), task.getCaseId(), null, Map.of("versionNo", nextVersion), null));
        return taskMapper.toResultResponse(newResult);
    }

    @Transactional(readOnly = true)
    public List<TaskResultResponse> listResults(UUID id, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        ElectronicCase electronicCase = requireCase(task.getCaseId());
        caseAccessPolicy.requireCanView(principal, electronicCase);
        
        return taskResultRepository.findByTaskIdOrderByVersionNoAsc(id).stream()
                .map(taskMapper::toResultResponse)
                .toList();
    }

    public TaskResponse approveResult(UUID id, String comment, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        requireTaskDepartmentHead(task, principal);

        TaskResult liveResult = taskResultRepository.findByTaskIdAndStatusIn(task.getId(), List.of(TaskResultStatus.SUBMITTED, TaskResultStatus.APPROVED))
                .orElseThrow(() -> new NotFoundException("NO_LIVE_RESULT", "No submitted result found to approve"));

        if (liveResult.getStatus() == TaskResultStatus.APPROVED) {
            return taskMapper.toResponse(task); // idempotent
        }

        liveResult.setStatus(TaskResultStatus.APPROVED);
        liveResult.setApprovedById(principal.userId());
        liveResult.setApprovedAt(Instant.now());
        taskResultRepository.save(liveResult);

        // TaskResult has no comment column (V7) - the optional approval comment is not silently
        // dropped, it goes into the audit row itself, which is the honest place for "why" on an
        // approval that leaves no other trace.
        auditWriter.write(AuditEvent.forCase(AuditAction.RESULT_APPROVED, "TaskResult", liveResult.getId(),
                task.getCaseId(), null, null, comment));
        return taskMapper.toResponse(task);
    }

    public TaskResponse returnTask(UUID id, ReturnTaskRequest request, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        requireTaskDepartmentHead(task, principal);

        if (request.reason() == null || request.reason().isBlank()) {
            throw new ValidationException("REVISION_REASON_REQUIRED", "Return to revision requires a reason");
        }

        task.setStatus(TaskStatus.RETURNED_FOR_REVISION);
        task.setRevisionCount(task.getRevisionCount() + 1);
        taskRepository.save(task);

        TaskResult liveResult = taskResultRepository.findByTaskIdAndStatusIn(task.getId(), List.of(TaskResultStatus.SUBMITTED, TaskResultStatus.APPROVED)).orElse(null);
        if (liveResult != null) {
            liveResult.setStatus(TaskResultStatus.REJECTED);
            liveResult.setReturnedById(principal.userId());
            liveResult.setReturnedAt(Instant.now());
            taskResultRepository.save(liveResult);
        }

        auditWriter.write(AuditEvent.forCase(AuditAction.TASK_RETURNED, "Task", task.getId(), task.getCaseId(), null, Map.of("reason", request.reason()), null));
        return taskMapper.toResponse(task);
    }

    public TaskResponse complete(UUID id, CompleteTaskRequest request, CustomUserPrincipal principal) {
        Task task = requireTask(id);
        requireTaskAssignee(task, principal);

        if (task.getStatus() == TaskStatus.COMPLETED) {
            // Idempotent replay (API_SPEC.md 6): same body, does not advance the workflow again.
            return taskMapper.toResponse(task);
        }
        // request.version() is not compared here: JPA's own @Version on Task already enforces real
        // optimistic locking at flush time (a genuine lost update throws
        // ObjectOptimisticLockingFailureException, mapped by GlobalExceptionHandler to the same
        // 409 CONCURRENT_MODIFICATION) - a redundant manual pre-check would need to track the
        // entity's version through every prior mutation (assign/start/submit-result each bump it),
        // which the client-visible contract does not actually require the caller to do correctly.

        WorkflowStage config = workflowStageRepository.findById(task.getWorkflowStageId()).orElseThrow();
        if (config.isRequiresResult()) {
            TaskResult liveResult = taskResultRepository.findByTaskIdAndStatusIn(task.getId(), List.of(TaskResultStatus.SUBMITTED, TaskResultStatus.APPROVED)).orElse(null);
            if (liveResult == null) {
                throw new IllegalStateTransitionException("RESULT_REQUIRED", "Task requires a submitted result to complete");
            }
            if (config.isApprovalRequired() && liveResult.getStatus() != TaskResultStatus.APPROVED) {
                throw new IllegalStateTransitionException("APPROVAL_REQUIRED", "Task result must be approved before completion");
            }
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        taskRepository.save(task);

        auditWriter.write(AuditEvent.forCase(AuditAction.TASK_COMPLETED, "Task", task.getId(), task.getCaseId(), null, null, null));
        
        // Advance workflow
        workflowEngine.completeStage(task.getCaseId(), task.getWorkflowStageId());
        
        return taskMapper.toResponse(task);
    }

    private Task requireTask(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Task " + id + " not found"));
    }

    private ElectronicCase requireCase(UUID caseId) {
        return caseRepository.findById(caseId).orElseThrow(() -> new NotFoundException("CASE_NOT_FOUND", "Case not found"));
    }

    private void requireTaskAssignee(Task task, CustomUserPrincipal principal) {
        if (principal == null || principal.userId() == null || !principal.userId().equals(task.getAssignedUserId())) {
            throw new AccessDeniedDomainException("NOT_TASK_ASSIGNEE", "You are not the assignee of this task");
        }
    }

    private void requireTaskDepartmentHead(Task task, CustomUserPrincipal principal) {
        if (principal == null || principal.departmentId() == null || !principal.departmentId().equals(task.getAssignedDepartmentId())) {
            throw new AccessDeniedDomainException("DEPARTMENT_SCOPE_VIOLATION", "Task belongs to a different department");
        }
        if (!caseAccessPolicy.rolesOf(principal).contains(RoleCode.DEPARTMENT_HEAD)) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED", "Only department heads can perform this action");
        }
    }
}
