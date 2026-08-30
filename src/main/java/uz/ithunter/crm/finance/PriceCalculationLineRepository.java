package uz.ithunter.crm.finance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCalculationLineRepository extends JpaRepository<PriceCalculationLine, UUID> {

    List<PriceCalculationLine> findByPriceCalculationIdOrderByLineNoAsc(UUID priceCalculationId);
}
