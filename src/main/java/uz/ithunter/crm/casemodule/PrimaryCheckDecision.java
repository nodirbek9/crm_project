package uz.ithunter.crm.casemodule;

/**
 * The five decisions of spec 4.7, mirroring {@code ck_case_decision} / {@code ck_pc_decision} in V5
 * (PLAN_REVIEW H4). ASSUMPTIONS.md A2: the spec does not state which categories permit which
 * decisions, so any decision is allowed for any category and the reason is what carries the
 * justification.
 */
public enum PrimaryCheckDecision {
    ACCEPTED, RETURNED_TO_APPLICANT, NON_APPLICABILITY_OPINION, ROUTE_CHANGED, REJECTED
}
