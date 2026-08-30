package uz.ithunter.crm.audit;

/**
 * The only way application code writes to the audit trail (FINAL_IMPLEMENTATION_ORDER.md Phase 4:
 * "{@code AuditWriter} port + JPA implementation, wired into every admin mutation").
 *
 * <p>A port rather than a repository call so that the actor, IP address, hash chain and the
 * {@code ck_audit_case_scope} rule are enforced in exactly one place. There is deliberately no
 * REST endpoint that posts an arbitrary audit event (API_SPEC.md 8: "There is no
 * {@code POST /audit}"), so this interface is the whole write surface.
 */
public interface AuditWriter {

    /**
     * Appends one row. Joins the caller's transaction when there is one, so an admin mutation and
     * its audit row commit or roll back together; opens its own when there is not (for example the
     * read-path {@code CONFIDENTIAL_DATA_ACCESSED} write, which happens after the response body is
     * built).
     */
    void write(AuditEvent event);
}
