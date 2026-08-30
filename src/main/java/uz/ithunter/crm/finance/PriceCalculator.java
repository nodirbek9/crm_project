package uz.ithunter.crm.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.CaseItem;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * The DEMO pricing formula of ASSUMPTIONS.md A3 (spec 12.2):
 * {@code total = sum(item.basePrice * item.quantity) * modeCoefficient + additionalWorkFees},
 * floored at the {@code MINIMUM_TOTAL} rule when one is configured.
 *
 * <p>Like {@link uz.ithunter.crm.casemodule.PrimaryCheckEvaluator} and
 * {@link uz.ithunter.crm.casemodule.engine.DeadlineCalculator}, this class has no dependencies at
 * all - no repository, no {@code ObjectMapper} - so U-05 … U-08 construct it with
 * {@code new PriceCalculator()}. The caller ({@link AccountingService}) resolves which
 * {@link PriceRule} rows apply to the case's service/mode/validity window from the database; this
 * class only ever does arithmetic over whatever rules it is handed.
 *
 * <p>The mode coefficient is applied PER LINE (one line per {@code CaseItem}), not once to the
 * aggregate: {@code lineTotal = basePrice * quantity * coefficient}. That composes to the same
 * total as applying it once at the end, and it is what lets each response line carry a meaningful,
 * self-contained {@code coefficient}/{@code lineTotal} pair (API_SPEC.md 5's
 * {@code PriceCalculationResponse.lines[]} shape) instead of a coefficient that only makes sense
 * next to every other line.
 */
@Component
public class PriceCalculator {

    private static final String ADDITIONAL_WORK_FEE_DESCRIPTION = "Additional work fee";

    public PriceCalculationResult calculate(List<CaseItem> items, ProcessingMode mode, List<PriceRule> rules) {
        if (items == null) {
            items = List.of();
        }
        if (rules == null) {
            rules = List.of();
        }
        BigDecimal basePrice = firstOfType(rules, PriceRuleType.BASE_PER_ITEM)
                .map(PriceRule::getBasePrice)
                .filter(price -> price != null)
                .orElse(BigDecimal.ZERO);
        BigDecimal coefficient = applicableModeRule(rules, mode)
                .map(PriceRule::getCoefficient)
                .filter(c -> c != null)
                .orElse(BigDecimal.ONE);

        List<PriceCalculationResult.Line> lines = new ArrayList<>();
        int lineNo = 1;
        for (CaseItem item : items) {
            BigDecimal quantity = item.getQuantity() == null ? BigDecimal.ONE : item.getQuantity();
            BigDecimal lineTotal = round(basePrice.multiply(quantity).multiply(coefficient));
            lines.add(new PriceCalculationResult.Line(lineNo++, item.getId(),
                    firstOfType(rules, PriceRuleType.BASE_PER_ITEM).map(PriceRule::getId).orElse(null),
                    item.getItemName(), quantity, basePrice, coefficient, lineTotal));
        }

        for (PriceRule feeRule : rulesOfType(rules, PriceRuleType.ADDITIONAL_WORK_FEE)) {
            BigDecimal fee = feeRule.getBasePrice() == null ? BigDecimal.ZERO : round(feeRule.getBasePrice());
            lines.add(new PriceCalculationResult.Line(lineNo++, null, feeRule.getId(),
                    ADDITIONAL_WORK_FEE_DESCRIPTION, BigDecimal.ONE, fee, BigDecimal.ONE, fee));
        }

        BigDecimal total = lines.stream()
                .map(PriceCalculationResult.Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal minimum = firstOfType(rules, PriceRuleType.MINIMUM_TOTAL)
                .map(PriceRule::getBasePrice)
                .filter(min -> min != null)
                .orElse(null);
        if (minimum != null && total.compareTo(minimum) < 0) {
            total = round(minimum);
        }

        return new PriceCalculationResult(total, List.copyOf(lines));
    }

    private java.util.Optional<PriceRule> applicableModeRule(List<PriceRule> rules, ProcessingMode mode) {
        return rulesOfType(rules, PriceRuleType.MODE_COEFFICIENT).stream()
                .filter(rule -> rule.getProcessingMode() == mode)
                .findFirst()
                .or(() -> rulesOfType(rules, PriceRuleType.MODE_COEFFICIENT).stream()
                        .filter(rule -> rule.getProcessingMode() == null)
                        .findFirst());
    }

    private java.util.Optional<PriceRule> firstOfType(List<PriceRule> rules, PriceRuleType type) {
        return rulesOfType(rules, type).stream().findFirst();
    }

    private List<PriceRule> rulesOfType(List<PriceRule> rules, PriceRuleType type) {
        return rules.stream().filter(rule -> rule.getRuleType() == type).toList();
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** The pure computation result - never persisted directly, {@link AccountingService} maps it. */
    public record PriceCalculationResult(BigDecimal total, List<Line> lines) {

        public record Line(int lineNo, UUID caseItemId, UUID priceRuleId, String description,
                BigDecimal quantity, BigDecimal unitPrice, BigDecimal coefficient, BigDecimal lineTotal) {
        }
    }
}
