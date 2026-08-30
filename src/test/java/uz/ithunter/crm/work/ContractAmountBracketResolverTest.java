package uz.ithunter.crm.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * TEST_MATRIX.md U-13: boundary behaviour at exactly 10/20/30M. Plain JUnit -
 * {@link ContractAmountBracketResolver} has no dependencies at all. ASSUMPTIONS.md A4: lower bound
 * inclusive, upper bound exclusive.
 */
class ContractAmountBracketResolverTest {

    @Test
    void belowTenMillionIsLt10m() {
        assertThat(ContractAmountBracketResolver.resolve(new BigDecimal("9999999.99")))
                .isEqualTo(ContractAmountBracket.LT_10M);
        assertThat(ContractAmountBracketResolver.resolve(BigDecimal.ZERO))
                .isEqualTo(ContractAmountBracket.LT_10M);
    }

    @Test
    void exactlyTenMillionIsM10_20mNotLt10m() {
        assertThat(ContractAmountBracketResolver.resolve(new BigDecimal("10000000")))
                .isEqualTo(ContractAmountBracket.M10_20M);
    }

    @Test
    void justBelowTwentyMillionIsStillM10_20m() {
        assertThat(ContractAmountBracketResolver.resolve(new BigDecimal("19999999.99")))
                .isEqualTo(ContractAmountBracket.M10_20M);
    }

    @Test
    void exactlyTwentyMillionIsM20_30mNotM10_20m() {
        assertThat(ContractAmountBracketResolver.resolve(new BigDecimal("20000000")))
                .isEqualTo(ContractAmountBracket.M20_30M);
    }

    @Test
    void justBelowThirtyMillionIsStillM20_30m() {
        assertThat(ContractAmountBracketResolver.resolve(new BigDecimal("29999999.99")))
                .isEqualTo(ContractAmountBracket.M20_30M);
    }

    @Test
    void exactlyThirtyMillionIsGt30mNotM20_30m() {
        assertThat(ContractAmountBracketResolver.resolve(new BigDecimal("30000000")))
                .isEqualTo(ContractAmountBracket.GT_30M);
    }

    @Test
    void wellAboveThirtyMillionIsGt30m() {
        assertThat(ContractAmountBracketResolver.resolve(new BigDecimal("999000000")))
                .isEqualTo(ContractAmountBracket.GT_30M);
    }

    @Test
    void nullAmountDefaultsToLt10mRatherThanThrowing() {
        assertThat(ContractAmountBracketResolver.resolve(null)).isEqualTo(ContractAmountBracket.LT_10M);
    }
}
