package uz.ithunter.crm.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ithunter.crm.finance.PriceCalculationStatus;
import uz.ithunter.crm.finance.PriceCalculationTrigger;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/**
 * API_SPEC.md 5's {@code PriceCalculationResponse}. {@code demoNotice} literally says the tariffs
 * are demonstration values (ASSUMPTIONS.md A3) - every response carries it, not just a Swagger note,
 * so a client integrating against this API cannot miss it.
 */
public record PriceCalculationResponse(
        UUID id,
        int calculationNo,
        ProcessingMode processingMode,
        BigDecimal calculatedTotal,
        String currency,
        PriceCalculationTrigger triggerReason,
        PriceCalculationStatus status,
        Instant calculatedAt,
        List<Line> lines,
        List<HistoryEntry> supersededHistory,
        String demoNotice) {

    public record Line(int lineNo, String description, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal coefficient, BigDecimal lineTotal, UUID caseItemId) {
    }

    public record HistoryEntry(UUID id, int calculationNo, ProcessingMode processingMode,
            BigDecimal calculatedTotal, PriceCalculationTrigger triggerReason,
            PriceCalculationStatus status, Instant calculatedAt) {
    }
}
