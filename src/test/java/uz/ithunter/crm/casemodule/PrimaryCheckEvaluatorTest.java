package uz.ithunter.crm.casemodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * TEST_MATRIX.md U-01 … U-04. Plain JUnit: {@link PrimaryCheckEvaluator} has no dependencies at all,
 * so these run without Spring and without a database - which is the point of keeping the grading rules
 * in a dependency-free component.
 *
 * <p>The rules themselves are ASSUMPTIONS.md A1 (+ A27 for the missing-required-field reading): the
 * specification names RED/YELLOW/GREEN but defines no criteria.
 */
class PrimaryCheckEvaluatorTest {

    private final PrimaryCheckEvaluator evaluator = new PrimaryCheckEvaluator();

    /** U-01: everything required is present -> GREEN. */
    @Test
    void greenWhenNothingIsMissing() {
        assertThat(evaluator.categoryFor(PrimaryCheckFacts.complete()))
                .isEqualTo(PrimaryCheckCategory.GREEN);
        assertThat(evaluator.categoryFrom(Map.of())).isEqualTo(PrimaryCheckCategory.GREEN);
        assertThat(evaluator.categoryFrom(null)).isEqualTo(PrimaryCheckCategory.GREEN);
    }

    /** U-02: only optional data missing -> YELLOW. */
    @Test
    void yellowWhenOnlyOptionalDataIsMissing() {
        PrimaryCheckFacts facts = new PrimaryCheckFacts(Set.of(), Set.of(), Set.of("secondaryPhone"), false);
        assertThat(evaluator.categoryFor(facts)).isEqualTo(PrimaryCheckCategory.YELLOW);

        assertThat(evaluator.categoryFrom(Map.of(
                PrimaryCheckEvaluator.KEY_MISSING_OPTIONAL_DATA, List.of("secondaryPhone"))))
                .isEqualTo(PrimaryCheckCategory.YELLOW);
    }

    /** U-03: a missing required document -> RED. */
    @Test
    void redWhenARequiredDocumentIsMissing() {
        PrimaryCheckFacts facts = new PrimaryCheckFacts(Set.of(), Set.of("passportCopy"), Set.of(), false);
        assertThat(evaluator.categoryFor(facts)).isEqualTo(PrimaryCheckCategory.RED);

        assertThat(evaluator.categoryFrom(Map.of(
                PrimaryCheckEvaluator.KEY_MISSING_REQUIRED_DOCUMENTS, "passportCopy")))
                .isEqualTo(PrimaryCheckCategory.RED);
    }

    /** ASSUMPTIONS.md A27: a missing required *field* is RED too, not YELLOW. */
    @Test
    void redWhenARequiredFieldIsMissingOrDataIsInconsistent() {
        assertThat(evaluator.categoryFor(
                new PrimaryCheckFacts(Set.of("tin"), Set.of(), Set.of("secondaryPhone"), false)))
                .isEqualTo(PrimaryCheckCategory.RED);
        assertThat(evaluator.categoryFor(new PrimaryCheckFacts(Set.of(), Set.of(), Set.of(), true)))
                .isEqualTo(PrimaryCheckCategory.RED);
    }

    /**
     * U-04: category and decision are two independent values. The evaluator produces a category and
     * has no {@code decisionFor(...)} at all, and every (category, decision) pairing is allowed
     * (ASSUMPTIONS.md A2) - so grading RED does not decide anything by itself.
     */
    @Test
    void categoryAndDecisionAreIndependent() {
        assertThat(PrimaryCheckEvaluator.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("decisionfor"));

        for (PrimaryCheckCategory category : PrimaryCheckCategory.values()) {
            for (PrimaryCheckDecision decision : PrimaryCheckDecision.values()) {
                assertThat(evaluator.isDecisionAllowed(category, decision))
                        .as("%s + %s", category, decision)
                        .isTrue();
            }
        }
        assertThat(evaluator.isDecisionAllowed(null, PrimaryCheckDecision.ACCEPTED)).isFalse();
    }

    /** {@code ck_pc_reason_required}: every decision except ACCEPTED needs a reason. */
    @Test
    void reasonIsRequiredForEveryDecisionExceptAccepted() {
        assertThat(evaluator.requiresReason(PrimaryCheckDecision.ACCEPTED)).isFalse();
        assertThat(evaluator.requiresReason(PrimaryCheckDecision.RETURNED_TO_APPLICANT)).isTrue();
        assertThat(evaluator.requiresReason(PrimaryCheckDecision.ROUTE_CHANGED)).isTrue();
        assertThat(evaluator.requiresReason(PrimaryCheckDecision.REJECTED)).isTrue();
        assertThat(evaluator.requiresReason(PrimaryCheckDecision.NON_APPLICABILITY_OPINION)).isTrue();
    }

    @Test
    void nullFactsAreARejectedProgrammingError() {
        assertThatThrownBy(() -> evaluator.categoryFor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
