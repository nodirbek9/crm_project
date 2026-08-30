package uz.ithunter.crm.casemodule;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseCommentRepository extends JpaRepository<CaseComment, UUID> {

    List<CaseComment> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
