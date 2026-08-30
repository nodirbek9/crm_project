package uz.ithunter.crm.task;

/** Mirrors {@code ck_task_result_status} in V7. Append-only chain — never deleted or edited. */
public enum TaskResultStatus {
    DRAFT, SUBMITTED, APPROVED, SUPERSEDED, REJECTED
}
