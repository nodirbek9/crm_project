package uz.ithunter.crm.casemodule;

/**
 * Mirrors {@code ck_case_comment_visibility} in V5, which allows exactly one value in this slice.
 *
 * <p>The single-valued enum is intentional rather than a dropped column: spec 13.5.1/17.8 make
 * "internal, never shown to the applicant" a property of the comment, and a future
 * applicant-visible comment kind would be a new value plus a migration - not a semantic change to
 * existing rows.
 */
public enum CommentVisibility {
    INTERNAL
}
