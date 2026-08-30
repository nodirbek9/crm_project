package uz.ithunter.crm.casemodule.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** One materialised {@code case_item} row (API_SPEC.md 4, spec 4.9). */
public record CaseItemResponse(
        UUID id,
        int lineNo,
        String itemName,
        String itemCode,
        BigDecimal quantity,
        String unit,
        String objectAddress,
        Map<String, Object> attributes) {
}
