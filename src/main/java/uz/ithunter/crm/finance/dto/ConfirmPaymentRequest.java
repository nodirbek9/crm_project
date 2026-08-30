package uz.ithunter.crm.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of {@code POST /accounting/cases/{id}/payment/confirm} (API_SPEC.md 5). */
public record ConfirmPaymentRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(max = 120) String externalReference,
        @Size(max = 1000) String note) {
}
