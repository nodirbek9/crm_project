package uz.ithunter.crm.audit;

import java.util.Set;

/**
 * Mirrors the {@code ck_audit_action} CHECK constraint of {@code V10__audit.sql} value for value
 * and in the same order. The DB rejects anything outside that list, so this enum and that
 * constraint must always be changed together (FINAL_DOMAIN_MODEL.md 9).
 *
 * <p>FINAL_IMPLEMENTATION_ORDER.md Phase 11 owns the guarantee that every value below is actually
 * emitted by some code path; Phase 4 only emits the administrative subset (see
 * {@link #isAdministrative()}) plus {@code CONFIDENTIAL_DATA_ACCESSED}.
 */
public enum AuditAction {

    CASE_CREATED,
    CASE_REGISTERED,
    PRIMARY_CHECK_COMPLETED,
    CATEGORY_ASSIGNED,
    PRIMARY_CHECK_DECISION_RECORDED,
    ROUTE_ASSIGNED,
    ROUTE_CHANGED,
    PROCESSING_MODE_SET,

    // --- accounting (spec 12.x) ---
    PRICE_CALCULATED,
    PRICE_RECALCULATED,
    PRICE_CONFIRMED,
    PRICE_CHANGED,
    CONTRACT_RECORDED,
    CONTRACT_SENT,
    PAYMENT_CONFIRMED,
    PAYMENT_STATUS_CHANGED,
    PAYMENT_OVERDUE,

    // --- workflow engine (spec 5.x) ---
    STAGE_ACTIVATED,
    STAGE_COMPLETED,

    // --- execution block (spec 7.x) ---
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_REASSIGNED,
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_RETURNED,
    RESULT_VERSION_CREATED,
    RESULT_APPROVED,

    // --- documents, approval, signing (spec 6.x, 7.13, 7.14) ---
    DOCUMENT_CREATED,
    DOCUMENT_VERSION_CREATED,
    APPROVAL_ROUND_STARTED,
    APPROVAL_SENT,
    APPROVAL_APPROVED,
    APPROVAL_REJECTED,
    APPROVAL_ROUND_COMPLETED,
    DOCUMENT_SIGNED,

    // --- performed works and case closure (spec 8.x, 14.x) ---
    PERFORMED_WORK_RECORDED,
    CASE_COMPLETED,
    CASE_REJECTED,
    CASE_RETURNED_TO_APPLICANT,

    // --- administrative events: no electronic case exists (PLAN_REVIEW C4, spec 16.10) ---
    USER_CREATED,
    USER_UPDATED,
    USER_BLOCKED,
    DEPARTMENT_CHANGED,
    POSITION_CHANGED,
    ROLE_PERMISSION_CHANGED,
    WORKFLOW_PUBLISHED,
    WORKFLOW_RETIRED,
    /**
     * OUT OF SCOPE in Phase 11 (confirmed by grep sweep):
     * - PRICE_RULE_CHANGED: emitted by GET /admin/price-rules (spec 16.18); that endpoint is
     *   optional (S-12 not in Phase 11 DoD) and not built here.
     * - REPORTING_ACCESS_CHANGED: spec section 18 (reporting/analytics) is explicitly out of
     *   scope for this entire slice per CLAUDE.md.
     * - CONFIDENTIAL_DATA_ACCESSED: paired with /admin/price-rules above; same scope reason.
     */
    PRICE_RULE_CHANGED,
    REFERENCE_DATA_CHANGED,
    REPORTING_ACCESS_CHANGED,
    CONFIDENTIAL_DATA_ACCESSED;

    /**
     * The actions {@code ck_audit_case_scope} allows with {@code case_id IS NULL}. Anything else
     * must carry a case id or the insert is rejected by the database. {@code USER_UPDATED},
     * {@code DEPARTMENT_CHANGED} and {@code POSITION_CHANGED} were added in Phase 4 for admin
     * edit operations - ASSUMPTIONS.md A15 records this as an extension of the "minimum set".
     */
    private static final Set<AuditAction> ADMINISTRATIVE = Set.of(
            USER_CREATED, USER_UPDATED, USER_BLOCKED, DEPARTMENT_CHANGED, POSITION_CHANGED,
            ROLE_PERMISSION_CHANGED, WORKFLOW_PUBLISHED, WORKFLOW_RETIRED, PRICE_RULE_CHANGED,
            REFERENCE_DATA_CHANGED, REPORTING_ACCESS_CHANGED, CONFIDENTIAL_DATA_ACCESSED);

    /** True when this action may be recorded without an electronic case (TEST_MATRIX.md A-04). */
    public boolean isAdministrative() {
        return ADMINISTRATIVE.contains(this);
    }
}
