package uz.ithunter.crm.casemodule;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseStageRepository extends JpaRepository<CaseStage, UUID> {

    List<CaseStage> findByCaseId(UUID caseId);

    /** Unique by {@code uq_case_stage} - that uniqueness is what makes activation idempotent. */
    Optional<CaseStage> findByCaseIdAndWorkflowStageId(UUID caseId, UUID workflowStageId);

    List<CaseStage> findByCaseIdAndStatus(UUID caseId, CaseStageStatus status);

    /**
     * The ACTIVE stages of a whole page of cases in one query - {@code GET /cases} needs each row's
     * current stage, and asking per case turns a 50-row listing into 51 round trips.
     */
    List<CaseStage> findByCaseIdInAndStatus(Collection<UUID> caseIds, CaseStageStatus status);

    /**
     * The parallel gate query of WORKFLOW_ENGINE_DESIGN.md 7 (spec 7.14): lock every required
     * sibling in {@code parallelGroup}, one row at a time, in ascending {@code id} order, then
     * evaluate whether they are all COMPLETED.
     *
     * <p>Pessimistic, not optimistic, and that is the whole point. Two siblings finishing at the
     * same instant each read the OTHER's row and write their OWN - {@code @Version} on
     * {@code case_stage} cannot see that conflict, so both threads would conclude "all required
     * siblings are done" and activate the dependent stage twice. The row lock serialises the two
     * gate evaluations; the loser then sees the winner's committed {@code COMPLETED} and the
     * idempotent {@code activateStage} keeps {@code activation_count = 1} (test C-02).
     *
     * <p>Locking is done row-by-row here rather than as a single
     * {@code SELECT ... FOR UPDATE ... ORDER BY id} (an earlier version of this method) precisely
     * because that single-query form does NOT guarantee lock-acquisition order in PostgreSQL: rows
     * are locked as the underlying scan visits them, which for a {@code case_id}/{@code
     * parallel_group} filter with no matching index is heap/scan order, not {@code id} order - the
     * {@code ORDER BY} only reorders the OUTPUT after locking already happened. Two concurrent
     * calls could therefore lock the same two sibling rows in opposite orders and deadlock
     * (reproduced by C-02 under genuine simultaneous completion, not merely a sequential replay).
     * Issuing N separate single-row {@code SELECT ... FOR UPDATE WHERE id = ?} statements, always
     * in the same ascending {@code id} sequence, makes every transaction request the same locks in
     * the same order - the standard fix for this class of deadlock.
     *
     * <p>{@code required = false} siblings are excluded in SQL rather than filtered in Java, so an
     * optional AUDIT stage left open can never block FINAL_REVIEW (tests U-10, W-06).
     */
    default List<CaseStage> lockRequiredSiblings(UUID caseId, String parallelGroup) {
        return findRequiredSiblingIds(caseId, parallelGroup).stream()
                .map(this::lockById)
                .toList();
    }

    @Query("""
            select s.id from CaseStage s
            where s.caseId = :caseId and s.parallelGroup = :parallelGroup and s.required = true
            order by s.id
            """)
    List<UUID> findRequiredSiblingIds(@Param("caseId") UUID caseId, @Param("parallelGroup") String parallelGroup);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CaseStage s where s.id = :id")
    CaseStage lockById(@Param("id") UUID id);
}
