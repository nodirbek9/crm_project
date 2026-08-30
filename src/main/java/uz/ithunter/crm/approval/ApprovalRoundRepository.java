package uz.ithunter.crm.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRoundRepository extends JpaRepository<ApprovalRound, UUID> {
    List<ApprovalRound> findByDocumentVersionId(UUID documentVersionId);
    Optional<ApprovalRound> findByDocumentVersionIdAndStatus(UUID documentVersionId, ApprovalRoundStatus status);

    /**
     * Whether ANY round for this case has ever reached the given status - used by
     * {@code CaseApprovalStateProvider} for the {@code APPROVAL_ROUND_COMPLETED} engine gate
     * (spec 13.7). A rejected round stays COMPLETED_REJECTED forever (a revision opens a NEW
     * round, spec 13.4); only the eventual all-approved round reaches COMPLETED_APPROVED, so "any
     * COMPLETED_APPROVED round exists" is correct even across a reject -> revise -> re-approve
     * cycle.
     */
    boolean existsByElectronicCaseIdAndStatus(UUID caseId, ApprovalRoundStatus status);
}
