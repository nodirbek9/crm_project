package uz.ithunter.crm.workflow;

/** Mirrors {@code ck_workflow_status} in V4. Copy-on-write versioning (spec 5.12, 16.11). */
public enum WorkflowStatus {
    DRAFT, ACTIVE, RETIRED
}
