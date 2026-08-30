package uz.ithunter.crm.casemodule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import uz.ithunter.crm.casemodule.PrimaryCheckCategory;
import uz.ithunter.crm.casemodule.PrimaryCheckDecision;

/**
 * Body of {@code POST /cases/{id}/primary-check} (API_SPEC.md 4, spec 4.5 - 4.7).
 *
 * <p>{@code category} and {@code decision} are two independent required fields (spec 1.5 vs 4.7, test
 * U-04): the grading of the data and what is done about it are separate facts, and ASSUMPTIONS.md A2
 * allows any decision for any category.
 *
 * <p>{@code reason} is mandatory for every decision except ACCEPTED - {@code ck_pc_reason_required} in
 * V5 enforces the same rule at the database level, and the service checks it first so the caller gets
 * {@code 400} with a field name instead of a constraint-violation stack trace (test I-09).
 *
 * <p>{@code newWorkflowId} is mandatory for, and only meaningful with, ROUTE_CHANGED
 * ({@code ck_pc_route_change}).
 *
 * <p>{@code checklist} is the specialist's findings; ASSUMPTIONS.md A27 documents the keys
 * {@code PrimaryCheckEvaluator} reads from it.
 */
public record PrimaryCheckRequest(
        @NotNull PrimaryCheckCategory category,
        @NotNull PrimaryCheckDecision decision,
        @Size(max = 2000) String reason,
        Map<String, Object> checklist,
        UUID newWorkflowId) {
}
