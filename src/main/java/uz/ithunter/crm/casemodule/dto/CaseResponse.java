package uz.ithunter.crm.casemodule.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.casemodule.PrimaryCheckCategory;
import uz.ithunter.crm.casemodule.PrimaryCheckDecision;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.workflow.StageType;

/**
 * The INTERNAL case view (API_SPEC.md 4). Staff-facing: it names internal stages, departments and the
 * pinned route version, none of which an applicant may see - that is
 * {@link ApplicantTrackingResponse}, built by its own mapper from its own projection, and the two must
 * never be merged "to avoid duplication" (spec 15.13, test S-07).
 *
 * <p>No JPA entity appears in this record or in any of its nested views, so nothing here can trigger a
 * lazy load after the transaction has closed ({@code spring.jpa.open-in-view: false}).
 *
 * <p>{@code version} is the optimistic-locking token the client echoes back on the next write, which
 * is what turns a lost update into {@code 409 CONCURRENT_MODIFICATION} instead of silent overwriting.
 */
public record CaseResponse(
        UUID id,
        String caseNumber,
        String applicationNumber,
        ApplicantRef applicant,
        ServiceRef service,
        CaseStatus status,
        StageRef currentStage,
        List<StageRef> activeStages,
        PrimaryCheckCategory primaryCheckCategory,
        PrimaryCheckDecision primaryCheckDecision,
        ProcessingMode processingMode,
        WorkflowRef workflow,
        DepartmentRef mainResponsibleDepartment,
        List<DepartmentRef> participatingDepartments,
        Instant dueAt,
        Instant paymentDueAt,
        boolean paymentOverdue,
        FinanceView finance,
        long version,
        Instant createdAt) {

    /** Enough of the applicant to identify them in a worklist; the full profile is its own endpoint. */
    public record ApplicantRef(UUID id, String type, String displayName, String tin, String phone) {
    }

    public record ServiceRef(UUID id, String code, String name) {
    }

    /**
     * An internal stage. {@code internalStatusLabel} is the staff-facing wording configured on
     * {@code workflow_stage} (spec 5.3) - deliberately a different string from the external stage name
     * the applicant sees (spec 5.11).
     */
    public record StageRef(
            UUID id,
            String code,
            String name,
            StageType stageType,
            String internalStatusLabel,
            int sequence,
            String parallelGroup,
            boolean required,
            CaseStageStatus status,
            Instant activatedAt,
            Instant dueAt,
            boolean overdue) {
    }

    /** The PINNED route version (spec 5.12): the case keeps this one even after a newer one is published. */
    public record WorkflowRef(UUID id, String code, int version) {
    }

    public record DepartmentRef(UUID id, String code, String name) {
    }

    /**
     * The finance block of API_SPEC.md 4. ASSUMPTIONS.md A28: null throughout Phase 7 - contract,
     * price and payment are Phase 8's tables. The shape ships now so the staff client does not have to
     * change when they land.
     */
    public record FinanceView(
            String contractNumber,
            BigDecimal totalAmount,
            BigDecimal confirmedAmount,
            BigDecimal debtAmount,
            String paymentStatus) {
    }
}
