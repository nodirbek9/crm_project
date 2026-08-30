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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One primary-check attempt (spec 4.5-4.7), mapping to {@code primary_check} in V5
 * (FINAL_DOMAIN_MODEL.md 4.3).
 *
 * <p>A separate entity rather than five more columns on the case, because the check is itself an
 * auditable, repeatable fact: a {@code RETURNED_TO_APPLICANT} followed by a resubmission produces a
 * SECOND row, and the case fields only mirror the latest one. {@code attemptNo} plus
 * {@code uq_primary_check_attempt (case_id, attempt_no)} is what makes that history explicit and
 * makes a duplicate attempt a database error rather than a silent extra row.
 *
 * <p>Note that {@code attemptNo} appears in V5 but not in FINAL_DOMAIN_MODEL.md 4.3's field list.
 * The migration wins under {@code ddl-auto = validate} (CLAUDE.md: "if an entity doesn't match, fix
 * the entity, not the migration"), and it is strictly more expressive than the doc's list.
 *
 * <p>This table has NO {@code created_at}/{@code updated_at} and no update trigger - {@code
 * performedAt} is the business timestamp and the row is not meant to be edited afterwards.
 */
@Entity
@Table(name = "primary_check")
@Getter
@Setter
@NoArgsConstructor
public class PrimaryCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo = 1;

    @Column(name = "performed_by_id", nullable = false)
    private UUID performedById;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private PrimaryCheckCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 40)
    private PrimaryCheckDecision decision;

    // ck_pc_reason_required: mandatory for every decision except ACCEPTED (spec 4.7, 15.8).
    @Column(name = "reason", length = 2000)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", nullable = false)
    private String checklist = "{}";

    // ck_pc_route_change: mandatory exactly when decision = ROUTE_CHANGED.
    @Column(name = "new_workflow_id")
    private UUID newWorkflowId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
