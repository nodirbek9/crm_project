package uz.ithunter.crm.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    Optional<Service> findByCode(String code);

    boolean existsByCode(String code);

    Page<Service> findByActive(boolean active, Pageable pageable);
}
