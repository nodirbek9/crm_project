package uz.ithunter.crm.document;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByElectronicCaseId(UUID caseId);

    /** Phase 11: links a Document to its originating Task for performed-work recording (spec 8.3). */
    Optional<Document> findFirstByTask_Id(UUID taskId);
}
