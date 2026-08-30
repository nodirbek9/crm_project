package uz.ithunter.crm.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.ithunter.crm.casemodule.CaseItem;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * TEST_MATRIX.md U-05 … U-08. Plain JUnit: {@link PriceCalculator} has no dependencies at all, so
 * these run without Spring and without a database - the DEMO formula is ASSUMPTIONS.md A3.
 */
class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator();

    private CaseItem item(BigDecimal quantity) {
        CaseItem item = new CaseItem();
        item.setItemName("Waste sample");
        item.setQuantity(quantity);
        return item;
    }

    private PriceRule rule(PriceRuleType type, ProcessingMode mode, BigDecimal basePrice, BigDecimal coefficient) {
        PriceRule rule = new PriceRule();
        rule.setRuleType(type);
        rule.setProcessingMode(mode);
        rule.setBasePrice(basePrice);
        rule.setCoefficient(coefficient);
        return rule;
    }

    /** U-05: sums per-item lines (base * quantity) times the mode coefficient. */
    @Test
    void sumsPerItemLinesTimesModeCoefficient() {
        List<CaseItem> items = List.of(item(new BigDecimal("2")), item(new BigDecimal("3")));
        List<PriceRule> rules = List.of(
                rule(PriceRuleType.BASE_PER_ITEM, null, new BigDecimal("100"), null),
                rule(PriceRuleType.MODE_COEFFICIENT, ProcessingMode.TRADITIONAL, null, new BigDecimal("1.0")));

        PriceCalculator.PriceCalculationResult result = calculator.calculate(items, ProcessingMode.TRADITIONAL, rules);

        assertThat(result.total()).isEqualByComparingTo("500.00");
        assertThat(result.lines()).extracting(l -> l.lineTotal())
                .containsExactly(new BigDecimal("200.00"), new BigDecimal("300.00"));
    }

    /** U-06: EXPEDITED coefficient produces a higher total than TRADITIONAL for the same items. */
    @Test
    void expeditedProducesHigherTotalThanTraditional() {
        List<CaseItem> items = List.of(item(new BigDecimal("2")));
        List<PriceRule> rules = List.of(
                rule(PriceRuleType.BASE_PER_ITEM, null, new BigDecimal("100"), null),
                rule(PriceRuleType.MODE_COEFFICIENT, ProcessingMode.TRADITIONAL, null, new BigDecimal("1.0")),
                rule(PriceRuleType.MODE_COEFFICIENT, ProcessingMode.EXPEDITED, null, new BigDecimal("1.5")));

        BigDecimal traditionalTotal = calculator.calculate(items, ProcessingMode.TRADITIONAL, rules).total();
        BigDecimal expeditedTotal = calculator.calculate(items, ProcessingMode.EXPEDITED, rules).total();

        assertThat(expeditedTotal).isGreaterThan(traditionalTotal);
        assertThat(expeditedTotal).isEqualByComparingTo("300.00");
        assertThat(traditionalTotal).isEqualByComparingTo("200.00");
    }

    /** U-07: a MINIMUM_TOTAL rule raises a below-minimum total. */
    @Test
    void minimumTotalRuleRaisesABelowMinimumTotal() {
        List<CaseItem> items = List.of(item(BigDecimal.ONE));
        List<PriceRule> rules = List.of(
                rule(PriceRuleType.BASE_PER_ITEM, null, new BigDecimal("10"), null),
                rule(PriceRuleType.MODE_COEFFICIENT, ProcessingMode.TRADITIONAL, null, new BigDecimal("1.0")),
                rule(PriceRuleType.MINIMUM_TOTAL, null, new BigDecimal("1000"), null));

        PriceCalculator.PriceCalculationResult result = calculator.calculate(items, ProcessingMode.TRADITIONAL, rules);

        assertThat(result.total()).isEqualByComparingTo("1000.00");
    }

    @Test
    void aboveMinimumTotalIsUnaffected() {
        List<CaseItem> items = List.of(item(new BigDecimal("100")));
        List<PriceRule> rules = List.of(
                rule(PriceRuleType.BASE_PER_ITEM, null, new BigDecimal("50"), null),
                rule(PriceRuleType.MODE_COEFFICIENT, ProcessingMode.TRADITIONAL, null, new BigDecimal("1.0")),
                rule(PriceRuleType.MINIMUM_TOTAL, null, new BigDecimal("1000"), null));

        PriceCalculator.PriceCalculationResult result = calculator.calculate(items, ProcessingMode.TRADITIONAL, rules);

        assertThat(result.total()).isEqualByComparingTo("5000.00");
    }

    /** U-08: the calculation emits exactly one line per CaseItem (no additional-work-fee rules here). */
    @Test
    void emitsOneLinePerCaseItem() {
        List<CaseItem> items = List.of(item(BigDecimal.ONE), item(BigDecimal.ONE), item(BigDecimal.ONE));
        List<PriceRule> rules = List.of(
                rule(PriceRuleType.BASE_PER_ITEM, null, new BigDecimal("10"), null),
                rule(PriceRuleType.MODE_COEFFICIENT, ProcessingMode.TRADITIONAL, null, new BigDecimal("1.0")));

        PriceCalculator.PriceCalculationResult result = calculator.calculate(items, ProcessingMode.TRADITIONAL, rules);

        assertThat(result.lines()).hasSize(3);
    }

    @Test
    void additionalWorkFeeRulesAddTheirOwnLine() {
        List<CaseItem> items = List.of(item(BigDecimal.ONE));
        List<PriceRule> rules = List.of(
                rule(PriceRuleType.BASE_PER_ITEM, null, new BigDecimal("10"), null),
                rule(PriceRuleType.MODE_COEFFICIENT, ProcessingMode.TRADITIONAL, null, new BigDecimal("1.0")),
                rule(PriceRuleType.ADDITIONAL_WORK_FEE, null, new BigDecimal("25"), null));

        PriceCalculator.PriceCalculationResult result = calculator.calculate(items, ProcessingMode.TRADITIONAL, rules);

        assertThat(result.lines()).hasSize(2);
        assertThat(result.total()).isEqualByComparingTo("35.00");
    }

    @Test
    void noRulesProducesZeroWithoutThrowing() {
        PriceCalculator.PriceCalculationResult result =
                calculator.calculate(List.of(item(BigDecimal.ONE)), ProcessingMode.TRADITIONAL, List.of());
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
