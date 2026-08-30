package uz.ithunter.crm.user;

/**
 * Mirrors {@code ck_role_code}. {@code HEAD_OF_CERTIFICATION_BODY} and {@code DEPARTMENT_HEAD} are
 * deliberately distinct roles (spec 3.2 vs 3.3) - do not merge them, see PLAN_REVIEW.md C2.
 */
public enum RoleCode {
    ADMIN, APPLICANT, ACCOUNTANT, HEAD_OF_CERTIFICATION_BODY, DEPARTMENT_HEAD, SPECIALIST, OPERATOR
}
