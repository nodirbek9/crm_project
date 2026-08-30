package uz.ithunter.crm.casemodule;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Grades a primary check into RED / YELLOW / GREEN (spec 1.5, 4.5, 4.6).
 *
 * <p>The specification names the three categories but defines no criteria, so the rules applied here
 * are ASSUMPTIONS.md A1's demonstration rules:
 *
 * <ul>
 *   <li>GREEN - every required field filled and every required document attached;
 *   <li>YELLOW - only optional/secondary data missing;
 *   <li>RED - a required document missing, or applicant data inconsistent.
 * </ul>
 *
 * <p>A1 leaves one gap: a missing required <i>field</i> (as opposed to a document) belongs to no
 * bucket. It is graded RED here - YELLOW is reserved for data the case can proceed without, and a
 * missing mandatory field is not that. ASSUMPTIONS.md A27 records the reading.
 *
 * <p>The class has no dependencies at all - no repository, no {@code ObjectMapper} - so U-01 … U-04
 * construct it with {@code new PrimaryCheckEvaluator()}. It is a {@code @Component} only so the
 * application service can inject it.
 *
 * <p><b>It never returns a decision.</b> Category (spec 1.5) and decision (spec 4.7) are two
 * independent facts about one check: the system grades the data, a human decides what to do about it,
 * and A2 permits any decision for any category. Test U-04 pins that independence, which is why there
 * is no {@code decisionFor(...)} method to accidentally start relying on.
 */
@Component
public class PrimaryCheckEvaluator {

    /** Keys read from the {@code checklist} object of {@code PrimaryCheckRequest} (ASSUMPTIONS.md A27). */
    public static final String KEY_MISSING_REQUIRED_FIELDS = "missingRequiredFields";
    public static final String KEY_MISSING_REQUIRED_DOCUMENTS = "missingRequiredDocuments";
    public static final String KEY_MISSING_OPTIONAL_DATA = "missingOptionalData";
    public static final String KEY_DATA_INCONSISTENT = "applicantDataInconsistent";
    public static final String KEY_REMARKS = "remarks";

    public PrimaryCheckCategory categoryFor(PrimaryCheckFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("facts are required");
        }
        if (facts.applicantDataInconsistent() || facts.anyRequiredMissing()) {
            return PrimaryCheckCategory.RED;
        }
        if (facts.anyOptionalMissing()) {
            return PrimaryCheckCategory.YELLOW;
        }
        return PrimaryCheckCategory.GREEN;
    }

    /**
     * Reads the specialist's checklist into facts. An absent or unparsable key means "nothing missing
     * of that kind": the checklist is optional in API_SPEC.md 4, and a blank one has to grade GREEN
     * rather than blow up mid-transaction.
     */
    public PrimaryCheckFacts factsFrom(Map<String, ?> checklist) {
        if (checklist == null || checklist.isEmpty()) {
            return PrimaryCheckFacts.complete();
        }
        return new PrimaryCheckFacts(
                asStringSet(checklist.get(KEY_MISSING_REQUIRED_FIELDS)),
                asStringSet(checklist.get(KEY_MISSING_REQUIRED_DOCUMENTS)),
                asStringSet(checklist.get(KEY_MISSING_OPTIONAL_DATA)),
                asBoolean(checklist.get(KEY_DATA_INCONSISTENT)));
    }

    /** Convenience for callers that hold a checklist and want the grade in one step. */
    public PrimaryCheckCategory categoryFrom(Map<String, ?> checklist) {
        return categoryFor(factsFrom(checklist));
    }

    /**
     * ASSUMPTIONS.md A2: the spec does not restrict decisions by category, so every pairing is legal
     * and the reason field carries the justification. Exists as a named method rather than as an
     * inline {@code true} so that the day the client does define a matrix, there is one place to put
     * it - and so that U-04 can assert the current rule instead of assuming it.
     */
    public boolean isDecisionAllowed(PrimaryCheckCategory category, PrimaryCheckDecision decision) {
        return category != null && decision != null;
    }

    /** {@code ck_pc_reason_required} in V5: every decision except ACCEPTED must carry a reason. */
    public boolean requiresReason(PrimaryCheckDecision decision) {
        return decision != null && decision != PrimaryCheckDecision.ACCEPTED;
    }

    private Set<String> asStringSet(Object raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null && !element.toString().isBlank()) {
                    values.add(element.toString().trim());
                }
            }
        } else if (!raw.toString().isBlank()) {
            // A single value or a comma-separated string, both of which clients do send.
            for (String part : raw.toString().split(",")) {
                if (!part.isBlank()) {
                    values.add(part.trim());
                }
            }
        }
        return values;
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        return raw != null && Boolean.parseBoolean(raw.toString().trim());
    }
}
