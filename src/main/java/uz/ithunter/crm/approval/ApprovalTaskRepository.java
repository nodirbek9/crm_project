package uz.ithunter.crm.approval;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {
    List<ApprovalTask> findByApprovalRoundId(UUID approvalRoundId);
    Page<ApprovalTask> findByParticipantUserId(UUID participantUserId, Pageable pageable);
}
