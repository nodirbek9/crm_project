package uz.ithunter.crm.audit;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The JPA implementation of the {@link AuditWriter} port.
 *
 * <p>{@code REQUIRED} propagation is the deliberate choice: an admin mutation and its audit row
 * must commit or roll back as one unit (a rolled-back change that left an audit row would be a
 * lie), while the read-path {@code CONFIDENTIAL_DATA_ACCESSED} write, which happens after the
 * response body is built and has no ambient transaction, still gets one of its own.
 *
 * <p>{@code saveAndFlush} rather than {@code save} so that {@code ck_audit_action},
 * {@code ck_audit_case_scope} and the hash-chain trigger fire at the call site instead of at
 * commit, where the stack trace would no longer point at the offending mutation.
 */
@Component
public class JpaAuditWriter implements AuditWriter {

    private final AuditLogRepository auditLogRepository;
    private final AuditActorResolver actorResolver;
    private final ObjectMapper objectMapper;

    public JpaAuditWriter(AuditLogRepository auditLogRepository, AuditActorResolver actorResolver,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.actorResolver = actorResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void write(AuditEvent event) {
        AuditActor actor = actorResolver.resolve();

        AuditLog row = new AuditLog();
        row.setAction(event.action());
        row.setEntityType(event.entityType());
        row.setEntityId(event.entityId());
        row.setCaseId(event.caseId());
        row.setTaskId(event.taskId());
        row.setOldValue(toJson(event.oldValue()));
        row.setNewValue(toJson(event.newValue()));
        row.setReason(event.reason());
        row.setUserId(actor.userId());
        row.setActorRoleCode(actor.roleCode());
        row.setActorDepartmentId(actor.departmentId());
        row.setIpAddress(actor.ipAddress());

        auditLogRepository.saveAndFlush(row);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            // Never swallowed: an unserialisable payload means the audit row would be incomplete,
            // and an incomplete audit row is worse than a failed mutation (spec 20.3).
            throw new IllegalStateException("Failed to serialize audit payload for " + value.keySet(), ex);
        }
    }
}
