package uz.ithunter.crm.work;

import java.math.BigDecimal;

/**
 * Pure, dependency-free helper (no Spring, no DB — same style as DeadlineCalculator).
 * Converts a contract amount (UZS) to its spec 8.4 bracket.
 * Boundary rule per ASSUMPTIONS.md A4: lower bound inclusive, upper bound exclusive.
 */
public final class ContractAmountBracketResolver {

    private static final BigDecimal M10 = new BigDecimal("10000000");
    private static final BigDecimal M20 = new BigDecimal("20000000");
    private static final BigDecimal M30 = new BigDecimal("30000000");

    private ContractAmountBracketResolver() {}

    public static ContractAmountBracket resolve(BigDecimal amount) {
        if (amount == null || amount.compareTo(M10) < 0) return ContractAmountBracket.LT_10M;
        if (amount.compareTo(M20) < 0) return ContractAmountBracket.M10_20M;
        if (amount.compareTo(M30) < 0) return ContractAmountBracket.M20_30M;
        return ContractAmountBracket.GT_30M;
    }
}
