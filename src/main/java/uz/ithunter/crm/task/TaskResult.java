package uz.ithunter.crm.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An immutable (on content) version of the work produced by a task (FINAL_DOMAIN_MODEL.md 5.2,
 * spec 7.13). Maps to {@code task_result} in V7.
 *
 * <p>V7 constraints this entity must respect:
 * <ul>
 *   <li>{@code uq_task_result_live} — at most one result per task in SUBMITTED or APPROVED status
 *       (partial unique index). A new submission must supersede the live one first.</li>
 *   <li>{@code ck_task_result_supersede} — {@code version_no = 1} needs nothing extra;
 *       {@code version_no > 1} REQUIRES both {@code supersedes_id} AND {@code revision_reason}.</li>
 *   <li>{@code ck_task_result_approved} — APPROVED status requires {@code approved_by_id} and
 *       {@code approved_at} set together.</li>
 *   <li>{@code tr_task_result_guard} trigger — {@code payload}, {@code version_no}, {@code
 *       author_id}, {@code created_at} are immutable after insert. Status/approval columns may change.
 *       Never update content in place — always insert a new version row.</li>
 * </ul>
 */
@Entity
@Table(name = "task_result")
@Getter
@Setter
@NoArgsConstructor
public class TaskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** Monotonically increasing within a task. {@code version_no = 1} for the first submission. */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** Specialist's work output as JSONB. Immutable after insert (see trigger). */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(name = "summary", length = 2000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskResultStatus status = TaskResultStatus.DRAFT;

    /** Immutable after insert (see trigger). */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Immutable after insert (see trigger). */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** Points at the result this version supersedes. Required when {@code version_no > 1}. */
    @Column(name = "supersedes_id")
    private UUID supersedesId;

    /** Required when {@code version_no > 1}. Why this version was needed. */
    @Column(name = "revision_reason", length = 2000)
    private String revisionReason;

    @Column(name = "returned_by_id")
    private UUID returnedById;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
