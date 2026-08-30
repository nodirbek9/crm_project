package uz.ithunter.crm.casemodule;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the reviewing specialist found while checking an application - the input to
 * {@link PrimaryCheckEvaluator}, and nothing more than that.
 *
 * <p>Deliberately a plain value object over four findings instead of the {@code Application} entity:
 * "which fields are mandatory for this service" is route configuration that changes per service and
 * per version, so grading the findings and discovering the findings are two different jobs. Keeping
 * them apart is what lets U-01 … U-03 test the grading rules with no Spring context and no database.
 *
 * <p>The sets are copied and never null, so a caller cannot mutate a graded fact afterwards.
 */
public record PrimaryCheckFacts(
        Set<String> missingRequiredFields,
        Set<String> missingRequiredDocuments,
        Set<String> missingOptionalData,
        boolean applicantDataInconsistent) {

    public PrimaryCheckFacts(Set<String> missingRequiredFields, Set<String> missingRequiredDocuments,
            Set<String> missingOptionalData, boolean applicantDataInconsistent) {
        this.missingRequiredFields = copyOf(missingRequiredFields);
        this.missingRequiredDocuments = copyOf(missingRequiredDocuments);
        this.missingOptionalData = copyOf(missingOptionalData);
        this.applicantDataInconsistent = applicantDataInconsistent;
    }

    /** Nothing missing, nothing contradictory - the GREEN case of ASSUMPTIONS.md A1. */
    public static PrimaryCheckFacts complete() {
        return new PrimaryCheckFacts(Set.of(), Set.of(), Set.of(), false);
    }

    public boolean anyRequiredMissing() {
        return !missingRequiredFields.isEmpty() || !missingRequiredDocuments.isEmpty();
    }

    public boolean anyOptionalMissing() {
        return !missingOptionalData.isEmpty();
    }

    private static Set<String> copyOf(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copy.add(value.trim());
            }
        }
        return Set.copyOf(copy);
    }
}
