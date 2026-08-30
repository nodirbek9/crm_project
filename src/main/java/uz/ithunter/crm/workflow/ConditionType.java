package uz.ithunter.crm.workflow;

/** Mirrors {@code ck_transition_condition} in V4. The route is data - no if/else chain. */
public enum ConditionType {
    ALWAYS, PRIMARY_CHECK_CATEGORY_IN, PRIMARY_CHECK_DECISION_IS, PROCESSING_MODE_IS,
    PAYMENT_STATE_SATISFIED, ALL_REQUIRED_PARALLEL_TASKS_DONE, APPROVAL_ROUND_COMPLETED, MANUAL_DECISION
}
