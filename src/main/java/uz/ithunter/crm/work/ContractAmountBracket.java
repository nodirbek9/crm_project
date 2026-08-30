package uz.ithunter.crm.work;

/**
 * Contract amount bracket for green-certification work types (spec 8.4, ASSUMPTIONS.md A4).
 * Boundary rule: lower bound inclusive, upper bound exclusive.
 * Amounts in UZS: [0,10M)→LT_10M, [10M,20M)→M10_20M, [20M,30M)→M20_30M, [30M,+∞)→GT_30M.
 */
public enum ContractAmountBracket {
    LT_10M,
    M10_20M,
    M20_30M,
    GT_30M
}
