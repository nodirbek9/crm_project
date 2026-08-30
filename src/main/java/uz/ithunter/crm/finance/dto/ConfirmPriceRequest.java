package uz.ithunter.crm.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Body of {@code POST /accounting/cases/{id}/price/confirm} (API_SPEC.md 5). No
 * {@code actualAmount} confirms the calculated value as-is; a different one requires
 * {@code reason} (spec 12.4, test I-12) - enforced in {@code AccountingService}, not here, since
 * "required together" is a cross-field rule bean validation on a record cannot express cleanly.
 */
public record ConfirmPriceRequest(
        @DecimalMin(value = "0", inclusive = true) BigDecimal actualAmount,
        @Size(max = 1000) String reason) {
}
