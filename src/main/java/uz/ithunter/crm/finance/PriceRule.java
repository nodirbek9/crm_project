package uz.ithunter.crm.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * A tariff row (FINAL_DOMAIN_MODEL.md 7.1, spec 12.2). Maps to {@code price_rule} in V6. Every
 * seeded row is {@code demo = true} per ASSUMPTIONS.md A3 - real tariffs come from the client.
 *
 * <p>V6's own comment states the rule this entity has to respect: "Rules are superseded by validity
 * window, never edited: an old calculation stays reproducible." A rule's money fields are therefore
 * never mutated after creation - superseding one means closing its {@code validTo} (or clearing
 * {@code active}) and inserting a new row, which is exactly what the admin API exposes.
 *
 * <p>{@code serviceId}/{@code workflowId}/{@code workTypeId} are raw ids rather than JPA relations:
 * {@code Workflow} does not exist until Phase 6, and a price rule never navigates to them - the
 * Phase 8 {@code PriceCalculator} looks rules up BY those ids.
 *
 * <p>{@code currency} needs {@code @JdbcTypeCode(SqlTypes.CHAR)}: the column is {@code char(3)},
 * which PostgreSQL reports as {@code bpchar}/JDBC {@code CHAR}, and a default String mapping would
 * be validated as {@code VARCHAR} and fail {@code ddl-auto: validate}.
 */
@Entity
@Table(name = "price_rule")
@Getter
@Setter
@NoArgsConstructor
public class PriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "work_type_id")
    private UUID workTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private PriceRuleType ruleType;

    /** Null means "applies to both modes" (ck_price_rule_mode allows NULL). */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", length = 20)
    private ProcessingMode processingMode;

    @Column(name = "base_price", precision = 18, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "coefficient", precision = 10, scale = 4)
    private BigDecimal coefficient;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "UZS";

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    /** ASSUMPTIONS.md A3: true for every Flyway-seeded tariff, so demo data is never mistaken for real. */
    @Column(name = "demo", nullable = false)
    private boolean demo = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
