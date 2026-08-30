package uz.ithunter.crm.casemodule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrimaryCheckRepository extends JpaRepository<PrimaryCheck, UUID> {

    List<PrimaryCheck> findByCaseIdOrderByAttemptNoAsc(UUID caseId);

    /** Feeds the next {@code attempt_no}; {@code uq_primary_check_attempt} is the real guard. */
    Optional<PrimaryCheck> findFirstByCaseIdOrderByAttemptNoDesc(UUID caseId);
}
