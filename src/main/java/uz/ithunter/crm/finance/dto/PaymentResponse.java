package uz.ithunter.crm.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ithunter.crm.finance.PaymentStatus;

/** API_SPEC.md 5's {@code PaymentResponse}. The applicant never sees this directly - only the trimmed view via {@code /cases/{id}/tracking}. */
public record PaymentResponse(
        UUID id,
        UUID caseId,
        PaymentStatus status,
        BigDecimal contractAmount,
        BigDecimal confirmedAmount,
        BigDecimal debtAmount,
        Instant waitingSince,
        Instant dueAt,
        boolean overdue,
        List<ConfirmationRef> confirmations,
        long version) {

    public record ConfirmationRef(UUID id, BigDecimal amount, UUID confirmedById, Instant confirmedAt,
            String note, String externalReference) {
    }
}
