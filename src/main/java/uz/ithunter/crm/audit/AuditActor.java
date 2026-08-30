package uz.ithunter.crm.audit;

import java.util.UUID;

/**
 * Who performed an audited action, resolved from the current request by
 * {@link AuditActorResolver}. Every field is nullable: a scheduler or a migration has no principal
 * and no IP (V10 documents {@code user_id IS NULL} as exactly that case).
 */
public record AuditActor(UUID userId, String roleCode, UUID departmentId, String ipAddress) {

    private static final AuditActor SYSTEM = new AuditActor(null, null, null, null);

    /** No authenticated principal on this thread - scheduler, startup task or an unauthenticated call. */
    public static AuditActor system() {
        return SYSTEM;
    }
}
