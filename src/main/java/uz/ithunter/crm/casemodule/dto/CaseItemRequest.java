package uz.ithunter.crm.casemodule.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * One line of the {@code PUT /cases/{id}/items} body (API_SPEC.md 4, spec 4.9).
 *
 * <p>There is deliberately no {@code lineNo} and no {@code id}: the PUT replaces the whole
 * composition, and the line number is the position in the submitted list. Letting the client choose
 * line numbers would mean either trusting it not to send duplicates (which
 * {@code uq_case_item_line} would then reject as a 500) or silently renumbering behind its back.
 */
public record CaseItemRequest(
        @NotBlank @Size(max = 255) String itemName,
        @Size(max = 60) String itemCode,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @NotBlank @Size(max = 20) String unit,
        @Size(max = 500) String objectAddress,
        Map<String, Object> attributes) {
}
