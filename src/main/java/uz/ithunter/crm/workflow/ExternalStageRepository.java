package uz.ithunter.crm.workflow;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalStageRepository extends JpaRepository<ExternalStage, UUID> {

    Optional<ExternalStage> findByCode(String code);

    boolean existsByCode(String code);

    Page<ExternalStage> findByActive(boolean active, Pageable pageable);
}
