package uz.ithunter.crm.audit;

import org.springframework.stereotype.Component;
import uz.ithunter.crm.audit.dto.AuditLogResponse;

@Component
public class AuditMapper {
    public AuditLogResponse toResponse(AuditLog log) {
        AuditLogResponse r = new AuditLogResponse();
        r.setId(log.getId());
        r.setSeq(log.getSeq());
        r.setCaseId(log.getCaseId());
        r.setTaskId(log.getTaskId());
        r.setUserId(log.getUserId());
        r.setActorRoleCode(log.getActorRoleCode());
        r.setActorDepartmentId(log.getActorDepartmentId());
        r.setAction(log.getAction() != null ? log.getAction().name() : null);
        r.setEntityType(log.getEntityType());
        r.setEntityId(log.getEntityId());
        r.setOldValue(log.getOldValue());
        r.setNewValue(log.getNewValue());
        r.setReason(log.getReason());
        r.setIpAddress(log.getIpAddress());
        r.setCreatedAt(log.getCreatedAt());
        return r;
    }
}
