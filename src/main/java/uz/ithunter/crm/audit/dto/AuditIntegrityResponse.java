package uz.ithunter.crm.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditIntegrityResponse {
    private boolean intact;
    private Long firstBrokenSeq;
}
