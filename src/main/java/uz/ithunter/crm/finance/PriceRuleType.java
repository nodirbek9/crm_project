package uz.ithunter.crm.finance;

/**
 * Mirrors {@code ck_price_rule_type} in V6. The four types are the pieces of the DEMO formula of
 * ASSUMPTIONS.md A3: {@code total = sum(item.basePrice * qty) * modeCoefficient + additionalWorkFees},
 * floored at {@code MINIMUM_TOTAL}.
 */
public enum PriceRuleType {
    BASE_PER_ITEM, MODE_COEFFICIENT, ADDITIONAL_WORK_FEE, MINIMUM_TOTAL
}
