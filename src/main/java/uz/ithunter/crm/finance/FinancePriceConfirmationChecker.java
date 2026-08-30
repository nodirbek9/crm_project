package uz.ithunter.crm.finance;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.casemodule.port.PriceConfirmationChecker;

/** The Phase 8 implementation of the seam {@code casemodule} declared for its item-lock check (A33). */
@Component
public class FinancePriceConfirmationChecker implements PriceConfirmationChecker {

    private final PriceCalculationRepository priceCalculationRepository;

    public FinancePriceConfirmationChecker(PriceCalculationRepository priceCalculationRepository) {
        this.priceCalculationRepository = priceCalculationRepository;
    }

    @Override
    public boolean isPriceConfirmed(UUID caseId) {
        return priceCalculationRepository
                .findFirstByCaseIdAndStatusIn(caseId, List.of(PriceCalculationStatus.CONFIRMED))
                .isPresent();
    }
}
