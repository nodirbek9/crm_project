package uz.ithunter.crm.finance.dto;

import jakarta.validation.constraints.NotNull;
import uz.ithunter.crm.shared.domain.ProcessingMode;

/** Body of {@code POST /accounting/cases/{id}/processing-mode} (API_SPEC.md 5). */
public record SetProcessingModeRequest(@NotNull ProcessingMode mode) {
}
