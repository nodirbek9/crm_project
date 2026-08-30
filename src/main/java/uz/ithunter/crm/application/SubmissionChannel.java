package uz.ithunter.crm.application;

/**
 * Spec 1.3. Mirrors {@code ck_service_channel} in V3 and the same CHECK on
 * {@code application.submission_channel} in V5.
 *
 * <p>{@code PAPER} is present on purpose (PLAN_REVIEW H7): a paper application is registered by an
 * operator on the applicant's behalf, so it is a real channel and not an afterthought.
 */
public enum SubmissionChannel {
    PERSONAL_CABINET, SINGLE_WINDOW, OTHER_SERVICE, PAPER
}
