package uz.ithunter.crm.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import uz.ithunter.crm.finance.ContractSentChannel;

/**
 * Body of {@code POST /accounting/cases/{id}/contract} (API_SPEC.md 5). All four fields are
 * required together - {@code ck_contract_sent} in V6 enforces the same rule at the database level
 * once {@code sent = true} (test I-13).
 */
public record RecordContractRequest(
        @NotBlank @Size(max = 60) String contractNumber,
        @NotNull LocalDate contractDate,
        @NotNull ContractSentChannel sentChannel,
        @NotNull Instant sentAt,
        @Size(max = 120) String invoiceReference,
        LocalDate invoiceDate) {
}
