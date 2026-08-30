package uz.ithunter.crm.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read + insert only. There is no update or delete method here and there must never be one
 * (spec 20.3); {@link JpaRepository#delete} and friends are inherited but the DB trigger and the
 * {@code crm_app} grant reject them, so a stray call fails loudly instead of silently succeeding.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByActionOrderBySeqDesc(AuditAction action);

    List<AuditLog> findByEntityTypeAndEntityIdOrderBySeqDesc(String entityType, UUID entityId);

    Page<AuditLog> findByCaseId(UUID caseId, Pageable pageable);

    Page<AuditLog> findByCaseIdOrderBySeqDesc(UUID caseId, Pageable pageable);

    /** Dynamic multi-filter query for GET /audit — all params nullable. */
    @Query("""
            select a from AuditLog a
            where (:caseId is null or a.caseId = :caseId)
              and (:userId is null or a.userId = :userId)
              and (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt <= :to)
            order by a.seq desc
            """)
    Page<AuditLog> search(
            @Param("caseId") UUID caseId,
            @Param("userId") UUID userId,
            @Param("action") AuditAction action,
            @Param("entityType") String entityType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** Backs {@code GET /audit/integrity} in a later phase; the SQL function is defined in V10. */
    @Query(value = "SELECT min(broken_seq) FROM verify_audit_chain()", nativeQuery = true)
    Long findFirstBrokenChainSeq();
}

