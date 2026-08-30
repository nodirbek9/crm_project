package uz.ithunter.crm.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One priced line of a {@link PriceCalculation} (spec 12.6, PLAN_REVIEW H2), mapping to
 * {@code price_calculation_line} in V6. Its own entity/repository rather than a JPA
 * {@code @OneToMany}, matching {@code CaseItem}'s precedent in this codebase: lines are never
 * navigated from the calculation, only queried by {@code priceCalculationId} when the response is
 * built.
 */
@Entity
@Table(name = "price_calculation_line")
@Getter
@Setter
@NoArgsConstructor
public class PriceCalculationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "price_calculation_id", nullable = false)
    private UUID priceCalculationId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Null for a line that is not tied to one submitted item (e.g. an additional-work-fee line). */
    @Column(name = "case_item_id")
    private UUID caseItemId;

    @Column(name = "price_rule_id")
    private UUID priceRuleId;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "coefficient", nullable = false, precision = 10, scale = 4)
    private BigDecimal coefficient = BigDecimal.ONE;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;
}
