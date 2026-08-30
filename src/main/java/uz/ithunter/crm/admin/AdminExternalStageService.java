package uz.ithunter.crm.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.admin.dto.CreateExternalStageRequest;
import uz.ithunter.crm.admin.dto.ExternalStageResponse;
import uz.ithunter.crm.admin.dto.UpdateExternalStageRequest;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.workflow.ExternalStage;
import uz.ithunter.crm.workflow.ExternalStageRepository;

@Service
public class AdminExternalStageService {

    private final ExternalStageRepository externalStageRepository;
    private final AuditWriter auditWriter;

    public AdminExternalStageService(ExternalStageRepository externalStageRepository, AuditWriter auditWriter) {
        this.externalStageRepository = externalStageRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExternalStageResponse> list(Pageable pageable) {
        return PageResponse.of(externalStageRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public ExternalStageResponse create(CreateExternalStageRequest request) {
        ExternalStage stage = new ExternalStage();
        stage.setCode(request.code());
        stage.setNameForApplicant(request.nameForApplicant());
        stage.setSequence(request.sequence());
        stage.setActive(true);
        stage = externalStageRepository.save(stage);

        auditWriter.write(AuditEvent.administrative(AuditAction.REFERENCE_DATA_CHANGED, "ExternalStage",
                stage.getId(), null, snapshot(stage)));
        return toResponse(stage);
    }

    @Transactional
    public ExternalStageResponse update(UUID id, UpdateExternalStageRequest request) {
        ExternalStage stage = externalStageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("External stage not found"));
        Map<String, Object> oldValue = snapshot(stage);

        stage.setNameForApplicant(request.nameForApplicant());
        stage.setSequence(request.sequence());
        stage.setActive(request.active());
        stage = externalStageRepository.save(stage);

        auditWriter.write(AuditEvent.administrative(AuditAction.REFERENCE_DATA_CHANGED, "ExternalStage",
                stage.getId(), oldValue, snapshot(stage)));
        return toResponse(stage);
    }

    private Map<String, Object> snapshot(ExternalStage stage) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", stage.getCode());
        map.put("nameForApplicant", stage.getNameForApplicant());
        map.put("sequence", stage.getSequence());
        map.put("active", stage.isActive());
        return map;
    }

    private ExternalStageResponse toResponse(ExternalStage stage) {
        return new ExternalStageResponse(stage.getId(), stage.getCode(), stage.getNameForApplicant(),
                stage.getSequence(), stage.isActive());
    }
}
