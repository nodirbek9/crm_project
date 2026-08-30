package uz.ithunter.crm.finance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Optional<Contract> findByCaseId(UUID caseId);

    boolean existsByCaseId(UUID caseId);
}
