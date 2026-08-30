package uz.ithunter.crm.workflow;

/** Mirrors {@code ck_stage_type} in V4 (spec 5.3). */
public enum StageType {
    PRIMARY_CHECK, ROUTING, ACCOUNTING, PAYMENT_CONTROL, EXECUTION, ENDORSEMENT,
    FINAL_REVIEW, SIGNING, COMPLETION, NON_APPLICABILITY_OPINION
}
