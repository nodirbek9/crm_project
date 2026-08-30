package uz.ithunter.crm.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByCodeAndStatus(String code, WorkflowStatus status);

    /**
     * The route(s) a new case may bind to (spec 5.12). Added for Phase 7's registration, which pins
     * {@code electronic_case.workflow_id} to an ACTIVE version and never to a DRAFT one.
     *
     * <p>Returns a list rather than an {@code Optional} because {@code uq_workflow_one_active} is
     * unique on {@code (code)}, not on {@code (service_id)}: one service may legitimately have several
     * ACTIVE route families. Ordering by code makes the choice deterministic - ASSUMPTIONS.md A29
     * records that the caller takes the first and why a real deployment would configure the mapping
     * explicitly instead.
     */
    List<Workflow> findByServiceIdAndStatusOrderByCodeAsc(UUID serviceId, WorkflowStatus status);

    @Query("select coalesce(max(w.version), 0) from Workflow w where w.code = :code")
    int findMaxVersionByCode(@Param("code") String code);
}
