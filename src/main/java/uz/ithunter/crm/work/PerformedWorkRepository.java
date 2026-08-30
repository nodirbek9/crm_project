package uz.ithunter.crm.work;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformedWorkRepository extends JpaRepository<PerformedWork, UUID> {

    List<PerformedWork> findByCaseId(UUID caseId);

    /** Mirrors the uq_performed_work_once partial index — NULL caseStageId safe via Optional. */
    Optional<PerformedWork> findByCaseIdAndWorkTypeIdAndCaseStageId(
            UUID caseId, UUID workTypeId, UUID caseStageId);

    @Query("""
            select pw from PerformedWork pw
            where (:caseId is null or pw.caseId = :caseId)
              and (:executorId is null or pw.executorUserId = :executorId)
              and (:departmentId is null or pw.departmentId = :departmentId)
              and (:workTypeId is null or pw.workTypeId = :workTypeId)
              and (:from is null or pw.performedAt >= :from)
              and (:to is null or pw.performedAt <= :to)
            order by pw.performedAt desc
            """)
    Page<PerformedWork> search(
            @Param("caseId") UUID caseId,
            @Param("executorId") UUID executorId,
            @Param("departmentId") UUID departmentId,
            @Param("workTypeId") UUID workTypeId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
