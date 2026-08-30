package uz.ithunter.crm.casemodule;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseItemRepository extends JpaRepository<CaseItem, UUID> {

    List<CaseItem> findByCaseIdOrderByLineNoAsc(UUID caseId);

    void deleteByCaseId(UUID caseId);
}
