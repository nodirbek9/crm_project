package uz.ithunter.crm.application;

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
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A service the organisation offers (FINAL_DOMAIN_MODEL.md 2.2, spec 5.2, 9.1, 9.2, 15.7). Maps to
 * {@code service} in V3.
 *
 * <p>Note the deliberate name clash with {@code org.springframework.stereotype.Service}: this is a
 * domain noun from the specification, so the entity keeps the name and the few Spring beans that
 * need both refer to this one by its fully qualified name.
 *
 * <p>{@code standaloneLaboratory} carries spec 9.1/9.2: laboratory analyses can be ordered as a
 * service in their own right, not only as a stage inside a certification route.
 *
 * <p>The submission channels are an {@code @ElementCollection} over
 * {@code service_submission_channel} rather than a bitmask or a comma-joined string, because the
 * DB already models it as a table with a CHECK constraint and a composite primary key.
 */
@Entity
@Table(name = "service")
@Getter
@Setter
@NoArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // Column type is text (unbounded), so no length attribute here on purpose.
    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "contract_required", nullable = false)
    private boolean contractRequired = true;

    @Column(name = "payment_required", nullable = false)
    private boolean paymentRequired = true;

    @Column(name = "standalone_laboratory", nullable = false)
    private boolean standaloneLaboratory = false;

    // EAGER because every response DTO needs the channels and spring.jpa.open-in-view is false,
    // so a lazy set would fail outside the service-layer transaction.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_submission_channel", joinColumns = @JoinColumn(name = "service_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private Set<SubmissionChannel> submissionChannels = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB-trigger managed (tr_service_updated -> set_updated_at()); never written by Hibernate.
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
