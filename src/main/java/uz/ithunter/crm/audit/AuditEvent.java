package uz.ithunter.crm.audit;

import java.util.Map;
import java.util.UUID;

/**
 * What happened, as handed to the {@link AuditWriter} port. Who did it, from where, and when is
 * NOT part of this record - {@link AuditActorResolver} derives that from the current request so
 * that no caller can spoof or forget it.
 *
 * <p>{@code oldValue}/{@code newValue} are plain maps; the writer serialises them to the
 * {@code jsonb} columns. Callers should put only what a reviewer needs to reconstruct the change,
 * never a password hash or a full entity dump.
 */
public record AuditEvent(
        AuditAction action,
        String entityType,
        UUID entityId,
        UUID caseId,
        UUID taskId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String reason) {

    public AuditEvent {
        if (action == null) {
            throw new IllegalArgumentException("audit action is required");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("audit entityType is required");
        }
        // ck_audit_case_scope would reject this at insert time; failing here names the bug better.
        if (caseId == null && !action.isAdministrative()) {
            throw new IllegalArgumentException(
                    "action " + action + " requires a caseId (ck_audit_case_scope)");
        }
    }

    /** An administrative event: no electronic case, {@code case_id IS NULL} (TEST_MATRIX.md A-04). */
    public static AuditEvent administrative(AuditAction action, String entityType, UUID entityId,
            Map<String, Object> oldValue, Map<String, Object> newValue) {
        return administrative(action, entityType, entityId, oldValue, newValue, null);
    }

    public static AuditEvent administrative(AuditAction action, String entityType, UUID entityId,
            Map<String, Object> oldValue, Map<String, Object> newValue, String reason) {
        if (!action.isAdministrative()) {
            throw new IllegalArgumentException(action + " is a case-scoped action, not an administrative one");
        }
        return new AuditEvent(action, entityType, entityId, null, null, oldValue, newValue, reason);
    }

    /** A case-scoped event. Used from Phase 5 onwards; kept here so the port has one shape. */
    public static AuditEvent forCase(AuditAction action, String entityType, UUID entityId, UUID caseId,
            Map<String, Object> oldValue, Map<String, Object> newValue, String reason) {
        return new AuditEvent(action, entityType, entityId, caseId, null, oldValue, newValue, reason);
    }
}
