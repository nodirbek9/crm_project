package uz.ithunter.crm.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.ithunter.crm.finance.PaymentStatus;

/**
 * Body of {@code POST /accounting/cases/{id}/payment/status} (API_SPEC.md 5) - the manual override
 * an authorized manager uses after a {@code PAYMENT_OVERDUE} flag (spec 12.9); never automatic.
 */
public record SetPaymentStatusRequest(@NotNull PaymentStatus status, @NotBlank String note) {
}
