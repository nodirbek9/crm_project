package uz.ithunter.crm.casemodule;

/**
 * The case LIFECYCLE, mirroring {@code ck_case_status} in V5 (FINAL_DOMAIN_MODEL.md 4.2).
 *
 * <p>PLAN_REVIEW M1 / FIX 7: this is deliberately NOT "where the case is in the route". Position in
 * the route lives in {@code electronic_case.current_stage_id} plus the {@code case_stage} rows, which
 * is why {@code current_stage_id} can be NULL while a parallel group is open and the status still
 * reads {@code IN_EXECUTION}.
 */
public enum CaseStatus {
    REGISTERED, PRIMARY_CHECK, PRIMARY_CHECK_DONE, IN_ACCOUNTING, WAITING_PAYMENT,
    IN_EXECUTION, FINAL_REVIEW, ON_SIGNING, COMPLETED, RETURNED, REJECTED
}
