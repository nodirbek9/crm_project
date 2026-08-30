package uz.ithunter.crm.work;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTypeRepository extends JpaRepository<WorkType, UUID> {

    Optional<WorkType> findByCode(String code);

    boolean existsByCode(String code);

    Page<WorkType> findByActive(boolean active, Pageable pageable);
}
