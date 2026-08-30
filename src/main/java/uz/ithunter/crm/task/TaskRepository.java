package uz.ithunter.crm.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** The uq_task_case_stage lookup — activator uses this to guard against double-creation. */
    Optional<Task> findByCaseStageId(UUID caseStageId);

    /**
     * Task-based view grant for SPECIALIST (SECURITY_SPEC.md 5, Phase 9 tightening).
     * {@link uz.ithunter.crm.casemodule.CaseAccessPolicy} uses this to replace the coarser
     * "department touches case" proxy.
     */
    boolean existsByCaseIdAndAssignedUserId(UUID caseId, UUID assignedUserId);

    /**
     * Task-based view grant for DEPARTMENT_HEAD and SPECIALIST when their department has a task in
     * the case (SECURITY_SPEC.md 5, Phase 9 tightening).
     */
    boolean existsByCaseIdAndAssignedDepartmentId(UUID caseId, UUID assignedDepartmentId);

    /**
     * {@code GET /tasks/my} — the caller's own open tasks ordered by deadline ascending.
     */
    Page<Task> findByAssignedUserId(UUID assignedUserId, Pageable pageable);

    /**
     * Filtered list behind {@code GET /tasks} — all nullable parameters are passed as {@code null}
     * to mean "no filter on that dimension".
     */
    @Query("""
            select t from Task t
            where (:status is null or t.status = :status)
              and (:caseId is null or t.caseId = :caseId)
              and (:departmentId is null or t.assignedDepartmentId = :departmentId)
              and (:assigneeId is null or t.assignedUserId = :assigneeId)
              and (:overdue is null or t.overdue = :overdue)
            """)
    Page<Task> search(
            @Param("status") TaskStatus status,
            @Param("caseId") UUID caseId,
            @Param("departmentId") UUID departmentId,
            @Param("assigneeId") UUID assigneeId,
            @Param("overdue") Boolean overdue,
            Pageable pageable);

    List<Task> findByCaseId(UUID caseId);
}
