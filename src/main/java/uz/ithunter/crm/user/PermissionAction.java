package uz.ithunter.crm.user;

/** The seven actions fixed by spec 16.4. Mirrors the {@code ck_permission_action} DB constraint. */
public enum PermissionAction {
    VIEW, CREATE, EDIT, ENDORSE, APPROVE, SIGN, BLOCK
}
