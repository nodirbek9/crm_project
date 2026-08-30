package uz.ithunter.crm.workflow;

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
 * A versioned route "card" (spec 5.2), maps to {@code workflow} in V4. Identified by
 * {@code (code, version)}; a published row is immutable - editing means copy-on-write into
 * {@code version+1} via {@code WorkflowDefinitionService}, never an UPDATE on a published row
 * (spec 5.12, 16.11). Deliberately has **no** {@code @Version} field: V4 has no optimistic-lock
 * column here on purpose ("a DB trigger is intentionally NOT used here: DRAFT rows must stay
 * editable" - V4's own comment) - immutability of published rows is a service-layer contract, not
 * a database one.
 */
@Entity
@Table(name = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @Column(name = "main_responsible_department_id", nullable = false)
    private UUID mainResponsibleDepartmentId;

    @Column(name = "expedited_allowed", nullable = false)
    private boolean expeditedAllowed;

    @Column(name = "contract_required", nullable = false)
    private boolean contractRequired = true;

    @Column(name = "payment_required", nullable = false)
    private boolean paymentRequired = true;

    @Column(name = "allow_execution_before_full_payment", nullable = false)
    private boolean allowExecutionBeforeFullPayment;

    @Column(name = "payment_waiting_days", nullable = false)
    private int paymentWaitingDays = 10;

    @Column(name = "total_deadline_days")
    private Integer totalDeadlineDays;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
