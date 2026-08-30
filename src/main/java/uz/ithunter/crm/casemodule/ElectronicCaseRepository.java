package uz.ithunter.crm.casemodule;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.ithunter.crm.shared.domain.ProcessingMode;

public interface ElectronicCaseRepository extends JpaRepository<ElectronicCase, UUID> {

    Optional<ElectronicCase> findByApplicationId(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);

    boolean existsByApplicantId(UUID applicantId);

    /**
     * A single case, with the applicant restriction bound INTO the query (SECURITY_SPEC.md 5).
     *
     * <p>Staff pass {@code null} and get the row; an applicant passes their own id and simply finds
     * nothing when the case is someone else's - which is also why the service can answer 404 there
     * without a second thought about leaking existence (SECURITY_SPEC.md 6). The explicit
     * {@code CaseAccessPolicy} call still happens afterwards; this is the layer underneath it, for the
     * day someone forgets that call.
     */
    @Query("""
            select c from ElectronicCase c
            where c.id = :caseId and (:applicantId is null or c.applicantId = :applicantId)
            """)
    Optional<ElectronicCase> findScopedById(@Param("caseId") UUID caseId,
            @Param("applicantId") UUID applicantId);

    /**
     * The applicant tracking read (spec 4.19, 15.5 - 15.7). Selects a constructor expression over six
     * columns rather than the entity, so the internal fields never enter the persistence context and
     * cannot be serialised by accident - see {@link CaseTrackingProjection} and test S-07.
     *
     * <p>Joined to {@code application} for the number the applicant actually quotes on the phone (the
     * internal case number is not shown) and to {@code service} for the public service name.
     */
    @Query("""
            select new uz.ithunter.crm.casemodule.CaseTrackingProjection(
                c.id, c.workflowId, c.status, a.number, a.submittedAt, s.name, c.dueAt)
            from ElectronicCase c
              join uz.ithunter.crm.application.Application a on a.id = c.applicationId
              join uz.ithunter.crm.application.Service s on s.id = c.serviceId
            where c.id = :caseId and (:applicantId is null or c.applicantId = :applicantId)
            """)
    Optional<CaseTrackingProjection> findTracking(@Param("caseId") UUID caseId,
            @Param("applicantId") UUID applicantId);

    /**
     * The filtered listing behind {@code GET /cases} (API_SPEC.md 4).
     *
     * <p>{@code applicantId} is a bound parameter of the query, not a post-filter - SECURITY_SPEC.md
     * 5's "repository-level defence in depth": a forgotten policy call still cannot leak another
     * applicant's rows, because the SQL itself never selects them. Staff callers pass {@code null}.
     *
     * <p>{@code departmentId} deliberately matches BOTH the main responsible department and the
     * participating set (spec 4.13) - a department that only owns one parallel stage still needs the
     * case in its list. The task-based clause of {@code canViewCase} cannot be expressed here until
     * Phase 9 creates {@code task}; ASSUMPTIONS.md A26 records that.
     *
     * <p>{@code stageCode} resolves against the ACTIVE stage rows rather than
     * {@code current_stage_id}, because {@code current_stage_id} is NULL while a parallel group is
     * open (PLAN_REVIEW M1) and "show me every case sitting in LABORATORY" must still work then.
     *
     * <p>{@code qLike} arrives already lowercased and already wrapped in {@code %} by the service:
     * building the pattern in Java rather than with {@code concat}/{@code cast} inside HQL keeps the
     * parameter unambiguously a {@code String} for Hibernate's type inference.
     */
    @Query("""
            select c from ElectronicCase c
            where (:applicantId is null or c.applicantId = :applicantId)
              and (:status is null or c.status = :status)
              and (:serviceId is null or c.serviceId = :serviceId)
              and (:mode is null or c.processingMode = :mode)
              and (:departmentId is null
                   or c.mainResponsibleDepartmentId = :departmentId
                   or exists (select 1 from ElectronicCase c2 join c2.participatingDepartmentIds d
                              where c2.id = c.id and d = :departmentId))
              and (:overdue is null
                   or (case when c.dueAt is not null and c.dueAt < :now and c.completedAt is null
                            then true else false end) = :overdue)
              and (:stageCode is null
                   or exists (select 1 from CaseStage cs
                              join uz.ithunter.crm.workflow.WorkflowStage ws on ws.id = cs.workflowStageId
                              where cs.caseId = c.id
                                and cs.status = uz.ithunter.crm.casemodule.CaseStageStatus.ACTIVE
                                and ws.code = :stageCode))
              and (:qLike is null
                   or lower(c.caseNumber) like :qLike
                   or exists (select 1 from uz.ithunter.crm.applicant.Applicant ap
                              where ap.id = c.applicantId
                                and (lower(coalesce(ap.orgName, '')) like :qLike
                                     or lower(coalesce(ap.lastName, '')) like :qLike)))
            """)
    Page<ElectronicCase> search(
            @Param("applicantId") UUID applicantId,
            @Param("status") CaseStatus status,
            @Param("serviceId") UUID serviceId,
            @Param("mode") ProcessingMode mode,
            @Param("departmentId") UUID departmentId,
            @Param("overdue") Boolean overdue,
            @Param("stageCode") String stageCode,
            @Param("qLike") String qLike,
            @Param("now") Instant now,
            Pageable pageable);
}
