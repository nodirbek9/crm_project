package uz.ithunter.crm.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * A unit of work assigned to a user or department (FINAL_DOMAIN_MODEL.md 5.1, spec 7.3).
 * Maps to {@code task} in V7.
 *
 * <p>Key V7 constraints this entity and service must respect:
 * <ul>
 *   <li>{@code uq_task_case_stage UNIQUE(case_stage_id)} — one live task per case stage; a return
 *       to revision REUSES this same row (spec 8.5) rather than inserting a second one;</li>
 *   <li>{@code ck_task_assigned} — CREATED/CANCELLED may have a null assignee; every other status
 *       requires {@code assigned_user_id}, {@code assigned_at}, {@code assigned_by_id} all set;</li>
 *   <li>{@code ck_task_completed} — COMPLETED requires {@code completed_at}.</li>
 * </ul>
 *
 * <p>{@code version} is the optimistic lock; the client echoes it back on the next write so that a
 * lost update becomes {@code 409 CONCURRENT_MODIFICATION} rather than a silent overwrite.
 */
@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "case_stage_id", nullable = false, unique = true)
    private UUID caseStageId;

    /** Denormalised for config lookups without joining through case_stage. */
    @Column(name = "workflow_stage_id", nullable = false)
    private UUID workflowStageId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "assigned_department_id", nullable = false)
    private UUID assignedDepartmentId;

    /** Null until a department head assigns the task (spec 5.5). */
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "assigned_by_id")
    private UUID assignedById;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TaskStatus status = TaskStatus.CREATED;

    /** Copied from case at task creation — drives the deadline (spec 5.8). */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", length = 20)
    private ProcessingMode processingMode;

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "overdue", nullable = false)
    private boolean overdue;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Incremented on each return-to-revision so reviewers can see how many iterations it took. */
    @Column(name = "revision_count", nullable = false)
    private int revisionCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
