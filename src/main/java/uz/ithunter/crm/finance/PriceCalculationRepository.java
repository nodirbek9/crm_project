package uz.ithunter.crm.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCalculationRepository extends JpaRepository<PriceCalculation, UUID> {

    /** The live one - {@code uq_price_calc_one_active} guarantees at most one row matches. */
    Optional<PriceCalculation> findFirstByCaseIdAndStatusIn(UUID caseId, List<PriceCalculationStatus> statuses);

    List<PriceCalculation> findByCaseIdOrderByCalculationNoDesc(UUID caseId);

    int countByCaseId(UUID caseId);
}
