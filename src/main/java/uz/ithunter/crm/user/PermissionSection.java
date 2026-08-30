package uz.ithunter.crm.user;

/** Resource groups a {@link PermissionAction} applies to. Mirrors {@code ck_permission_section}. */
public enum PermissionSection {
    APPLICATION, CASE, PRIMARY_CHECK, TASK, DOCUMENT, APPROVAL, FINANCE, PERFORMED_WORK,
    WORKFLOW_CONFIG, USER_ADMIN, REFERENCE_DATA, REPORTING, AUDIT
}
