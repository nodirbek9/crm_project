package uz.ithunter.crm.finance;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRuleRepository extends JpaRepository<PriceRule, UUID> {

    Page<PriceRule> findByActive(boolean active, Pageable pageable);

    Page<PriceRule> findByServiceId(UUID serviceId, Pageable pageable);

    Page<PriceRule> findByServiceIdAndActive(UUID serviceId, boolean active, Pageable pageable);
}
