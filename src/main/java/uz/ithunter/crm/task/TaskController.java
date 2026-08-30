package uz.ithunter.crm.task;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.task.dto.ApproveResultRequest;
import uz.ithunter.crm.task.dto.AssignTaskRequest;
import uz.ithunter.crm.task.dto.CompleteTaskRequest;
import uz.ithunter.crm.task.dto.ReturnTaskRequest;
import uz.ithunter.crm.task.dto.SubmitTaskResultRequest;
import uz.ithunter.crm.task.dto.TaskResponse;
import uz.ithunter.crm.task.dto.TaskResultResponse;
import uz.ithunter.crm.task.dto.TaskSummary;

/**
 * Controller for execution block operations (API_SPEC.md §6).
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TASK:VIEW')")
    public PageResponse<TaskSummary> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) Boolean overdue,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.list(status, caseId, departmentId, assigneeId, overdue, pageable, principal);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<TaskSummary> listMyTasks(Pageable pageable, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.listMyTasks(pageable, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK:VIEW')")
    public TaskResponse get(@PathVariable UUID id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.get(id, principal);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('TASK:EDIT')")
    public TaskResponse assign(@PathVariable UUID id, @RequestBody @Valid AssignTaskRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.assign(id, request, principal);
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAuthority('TASK:EDIT')")
    public TaskResponse reassign(@PathVariable UUID id, @RequestBody @Valid AssignTaskRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.reassign(id, request, principal);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('TASK:EDIT')")
    public TaskResponse start(@PathVariable UUID id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.start(id, principal);
    }

    @PostMapping("/{id}/results")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TASK:EDIT')")
    public TaskResultResponse submitResult(@PathVariable UUID id, @RequestBody @Valid SubmitTaskResultRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.submitResult(id, request, principal);
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("hasAuthority('TASK:VIEW')")
    public List<TaskResultResponse> listResults(@PathVariable UUID id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.listResults(id, principal);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TASK:EDIT')")
    public TaskResponse complete(@PathVariable UUID id, @RequestBody @Valid CompleteTaskRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.complete(id, request, principal);
    }

    @PostMapping("/{id}/approve-result")
    @PreAuthorize("hasAuthority('TASK:APPROVE')")
    public TaskResponse approveResult(@PathVariable UUID id, @RequestBody(required = false) ApproveResultRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        String comment = request != null ? request.comment() : null;
        return taskService.approveResult(id, comment, principal);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('TASK:APPROVE')")
    public TaskResponse returnTask(@PathVariable UUID id, @RequestBody @Valid ReturnTaskRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return taskService.returnTask(id, request, principal);
    }
}
