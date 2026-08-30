package uz.ithunter.crm.document;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);
    Optional<DocumentVersion> findByDocumentIdAndVersionNo(UUID documentId, int versionNo);
}
