package uz.ithunter.crm.audit.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class AuditLogResponse {
    private UUID id;
    private Long seq;
    private UUID caseId;
    private UUID taskId;
    private UUID userId;
    private String actorRoleCode;
    private UUID actorDepartmentId;
    private String action;
    private String entityType;
    private UUID entityId;
    private String oldValue;
    private String newValue;
    private String reason;
    private String ipAddress;
    private Instant createdAt;
}
