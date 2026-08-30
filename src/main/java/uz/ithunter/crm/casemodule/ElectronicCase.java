package uz.ithunter.crm.casemodule;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * The central object of the system (spec 1.4), mapping to {@code electronic_case} in V5
 * (FINAL_DOMAIN_MODEL.md 4.2).
 *
 * <p>Three things about this entity are load-bearing and should not be "tidied up":
 * <ul>
 *   <li>{@code workflowId} pins the exact workflow VERSION resolved at registration (spec 5.12).
 *       It is never re-resolved by code, so publishing v2 cannot alter a case running on v1 (test
 *       W-11). The single exception is an explicit {@code ROUTE_CHANGED} primary-check decision,
 *       which is a deliberate, audited, user-taken action (spec 4.7).</li>
 *   <li>{@code status} is a lifecycle, {@code currentStageId} is the position in the route
 *       (PLAN_REVIEW M1 / FIX 7). {@code currentStageId} is deliberately NULL while several parallel
 *       stages are active - the honest answer to "which stage" is then "several", and the applicant
 *       sees the mapped external stage anyway (test W-03).</li>
 *   <li>{@code version} is THE workflow concurrency guard (WORKFLOW_ENGINE_DESIGN.md 12). The
 *       parallel gate additionally takes a pessimistic lock on sibling {@code case_stage} rows,
 *       because it reads sibling rows and writes a different one.</li>
 * </ul>
 *
 * <p>Foreign keys are raw UUIDs rather than {@code @ManyToOne} associations, matching every other
 * entity in this codebase: {@code spring.jpa.open-in-view = false} makes lazy proxies a liability,
 * and the service layer loads what it needs explicitly.
 */
@Entity
@Table(name = "electronic_case")
@Getter
@Setter
@NoArgsConstructor
public class ElectronicCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_number", nullable = false, unique = true, length = 40)
    private String caseNumber;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    /** Denormalised from the application so ownership checks and the applicant index need no join. */
    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    /** The pinned workflow VERSION (spec 5.12) - see the class javadoc. */
    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CaseStatus status = CaseStatus.REGISTERED;

    /** NULL while a parallel group is open - by design, not by omission (test W-03). */
    @Column(name = "current_stage_id")
    private UUID currentStageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_check_category", length = 10)
    private PrimaryCheckCategory primaryCheckCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_check_decision", length = 40)
    private PrimaryCheckDecision primaryCheckDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", length = 20)
    private ProcessingMode processingMode;

    // ck_case_mode_audit: a non-null mode requires both of these - spec 1.9, the mode is always set
    // by a person (accounting), never implicitly. Phase 8 owns the endpoint that writes them.
    @Column(name = "processing_mode_set_by_id")
    private UUID processingModeSetById;

    @Column(name = "processing_mode_set_at")
    private Instant processingModeSetAt;

    @Column(name = "main_responsible_department_id", nullable = false)
    private UUID mainResponsibleDepartmentId;

    // EAGER for the same reason as Service.submissionChannels: every case DTO needs it and
    // open-in-view is false, so a lazy set would fail outside the service transaction.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_participating_department", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "department_id", nullable = false)
    private Set<UUID> participatingDepartmentIds = new LinkedHashSet<>();

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    /** Set by Phase 8's waiting scheduler; the system never auto-rejects on it (spec 12.9). */
    @Column(name = "payment_overdue", nullable = false)
    private boolean paymentOverdue;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB-trigger managed (tr_case_updated -> set_updated_at()); never written by Hibernate.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
