package uz.ithunter.crm.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import uz.ithunter.crm.finance.ContractSentChannel;

/** API_SPEC.md 5's {@code ContractResponse}. */
public record ContractResponse(
        UUID id,
        UUID caseId,
        String contractNumber,
        LocalDate contractDate,
        BigDecimal calculatedAmount,
        BigDecimal actualAmount,
        UUID amountChangedById,
        Instant amountChangedAt,
        String amountChangeReason,
        String currency,
        boolean sent,
        Instant sentAt,
        ContractSentChannel sentChannel,
        String invoiceReference,
        LocalDate invoiceDate,
        long version) {
}
