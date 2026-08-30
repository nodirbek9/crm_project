package uz.ithunter.crm.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.audit.dto.AuditIntegrityResponse;
import uz.ithunter.crm.audit.dto.AuditLogResponse;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.CaseAccessPolicy;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.shared.exception.NotFoundException;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ElectronicCaseRepository caseRepository;
    private final CaseAccessPolicy caseAccessPolicy;
    private final AuditMapper mapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        ElectronicCaseRepository caseRepository,
                        CaseAccessPolicy caseAccessPolicy,
                        AuditMapper mapper) {
        this.auditLogRepository = auditLogRepository;
        this.caseRepository = caseRepository;
        this.caseAccessPolicy = caseAccessPolicy;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(UUID caseId, UUID userId, String actionStr,
                                         String entityType, Instant from, Instant to,
                                         Pageable pageable) {
        AuditAction action = actionStr != null ? AuditAction.valueOf(actionStr) : null;
        return auditLogRepository.search(caseId, userId, action, entityType, from, to, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchByCase(UUID caseId, CustomUserPrincipal principal, Pageable pageable) {
        ElectronicCase eCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("CASE_NOT_FOUND", "Case not found"));
        caseAccessPolicy.requireCanView(principal, eCase);
        return auditLogRepository.findByCaseIdOrderBySeqDesc(caseId, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AuditIntegrityResponse checkIntegrity() {
        Long firstBroken = auditLogRepository.findFirstBrokenChainSeq();
        return new AuditIntegrityResponse(firstBroken == null, firstBroken);
    }
}
