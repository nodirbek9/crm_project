package uz.ithunter.crm.application;

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
 * Maps to {@code application} in V5 (FINAL_DOMAIN_MODEL.md 4.1). {@code formData} is stored
 * pre-serialized (same pattern as {@code AuditLog}'s JSON columns) - route-configured mandatory
 * fields plus, in this phase, the item composition submitted with the application under an
 * {@code items} key (there is no {@code application_item} table; {@code CaseItem} rows only get
 * materialized at Phase 7's {@code register}, reading this same JSON).
 */
@Entity
@Table(name = "application")
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "number", nullable = false, unique = true, length = 40)
    private String number;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_channel", nullable = false, length = 30)
    private uz.ithunter.crm.application.SubmissionChannel submissionChannel;

    /** The staff member who registered this on the applicant's behalf; mandatory when channel = PAPER. */
    @Column(name = "registered_by_id")
    private UUID registeredById;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", nullable = false)
    private String formData;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB-trigger managed (tr_application_updated -> set_updated_at()); never written by Hibernate.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
