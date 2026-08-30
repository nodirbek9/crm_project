package uz.ithunter.crm.casemodule;

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

/**
 * The runtime stage instance (FINAL_DOMAIN_MODEL.md 4.5), mapping to {@code case_stage} in V5.
 *
 * <p>The workflow configuration is immutable, so per-case stage state needs its own row. All stages
 * are materialised up front at registration rather than lazily (WORKFLOW_ENGINE_DESIGN.md 3): that
 * is what turns the parallel gate into a plain indexed read instead of a graph walk, and it makes
 * {@code uq_case_stage (case_id, workflow_stage_id)} the structural guarantee behind idempotent
 * activation - a replayed activation finds the row already {@code ACTIVE} and no-ops.
 *
 * <p>{@code parallelGroup} and {@code required} are COPIED from {@link
 * uz.ithunter.crm.workflow.WorkflowStage} at materialisation time on purpose. The gate query then
 * filters on this table alone, and a case keeps the grouping it was registered with even though a
 * published workflow version is immutable anyway - it also keeps the {@code FOR UPDATE} of spec 7.14
 * on one table.
 */
@Entity
@Table(name = "case_stage")
@Getter
@Setter
@NoArgsConstructor
public class CaseStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "workflow_stage_id", nullable = false)
    private UUID workflowStageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CaseStageStatus status = CaseStageStatus.PENDING;

    @Column(name = "parallel_group", length = 60)
    private String parallelGroup;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    // ck_case_stage_activated: anything other than PENDING requires this to be set.
    @Column(name = "activated_at")
    private Instant activatedAt;

    // ck_case_stage_completed: COMPLETED requires this to be set.
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "overdue", nullable = false)
    private boolean overdue;

    /** Incremented on every real activation - test C-02 asserts it stays 1 under a race. */
    @Column(name = "activation_count", nullable = false)
    private int activationCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB-trigger managed (tr_case_stage_updated -> set_updated_at()); never written by Hibernate.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
