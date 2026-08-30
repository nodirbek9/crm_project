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
 * An immutable calculation snapshot (FINAL_DOMAIN_MODEL.md 7.2, spec 1.11-1.12, 12.3-12.4). Maps
 * to {@code price_calculation} in V6. A recalculation never updates this row - it inserts a NEW
 * one and supersedes the old {@code ACTIVE} row, which is what {@code ruleSetSnapshot} is for:
 * the exact rules a past calculation used stay reconstructable even after the live
 * {@code price_rule} rows change.
 *
 * <p>{@code uq_price_calc_one_active} (a partial unique index on {@code case_id} WHERE status IN
 * {@code (ACTIVE, CONFIRMED)}) is the DB's own guarantee that a case never has two "live"
 * calculations at once - the service must supersede the previous one in the SAME transaction
 * before inserting a new ACTIVE row, or the insert fails.
 */
@Entity
@Table(name = "price_calculation")
@Getter
@Setter
@NoArgsConstructor
public class PriceCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "calculation_no", nullable = false)
    private int calculationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", nullable = false, length = 20)
    private ProcessingMode processingMode;

    @Column(name = "calculated_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal calculatedTotal;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "UZS";

    /** The resolved {@link PriceRule} rows and the per-line breakdown, as JSON (spec 12.3). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_set_snapshot", nullable = false)
    private String ruleSetSnapshot = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 30)
    private PriceCalculationTrigger triggerReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PriceCalculationStatus status = PriceCalculationStatus.ACTIVE;

    /** The row this one replaces (spec 12.3's "recalculation"), or {@code null} for the first one. */
    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "calculated_by_id")
    private UUID calculatedById;
}
