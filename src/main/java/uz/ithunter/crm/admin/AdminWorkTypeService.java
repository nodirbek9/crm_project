package uz.ithunter.crm.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.admin.dto.CreateWorkTypeRequest;
import uz.ithunter.crm.admin.dto.UpdateWorkTypeRequest;
import uz.ithunter.crm.admin.dto.WorkTypeResponse;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.work.WorkType;
import uz.ithunter.crm.work.WorkTypeRepository;

@Service
public class AdminWorkTypeService {

    private final WorkTypeRepository workTypeRepository;
    private final AuditWriter auditWriter;

    public AdminWorkTypeService(WorkTypeRepository workTypeRepository, AuditWriter auditWriter) {
        this.workTypeRepository = workTypeRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkTypeResponse> list(Pageable pageable) {
        return PageResponse.of(workTypeRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public WorkTypeResponse create(CreateWorkTypeRequest request) {
        WorkType workType = new WorkType();
        workType.setCode(request.code());
        workType.setName(request.name());
        workType.setServiceScope(request.serviceScope());
        workType.setStageKind(request.stageKind());
        workType.setRequiresContractAmountBracket(request.requiresContractAmountBracket());
        workType.setBasisDocumentDescription(request.basisDocumentDescription());
        workType.setActive(true);
        workType = workTypeRepository.save(workType);

        auditWriter.write(AuditEvent.administrative(AuditAction.REFERENCE_DATA_CHANGED, "WorkType",
                workType.getId(), null, snapshot(workType)));
        return toResponse(workType);
    }

    @Transactional
    public WorkTypeResponse update(UUID id, UpdateWorkTypeRequest request) {
        WorkType workType = workTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Work type not found"));
        Map<String, Object> oldValue = snapshot(workType);

        workType.setName(request.name());
        workType.setServiceScope(request.serviceScope());
        workType.setStageKind(request.stageKind());
        workType.setRequiresContractAmountBracket(request.requiresContractAmountBracket());
        workType.setBasisDocumentDescription(request.basisDocumentDescription());
        workType.setActive(request.active());
        workType = workTypeRepository.save(workType);

        auditWriter.write(AuditEvent.administrative(AuditAction.REFERENCE_DATA_CHANGED, "WorkType",
                workType.getId(), oldValue, snapshot(workType)));
        return toResponse(workType);
    }

    private Map<String, Object> snapshot(WorkType workType) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", workType.getCode());
        map.put("name", workType.getName());
        map.put("serviceScope", workType.getServiceScope());
        map.put("stageKind", workType.getStageKind());
        map.put("requiresContractAmountBracket", workType.isRequiresContractAmountBracket());
        map.put("active", workType.isActive());
        return map;
    }

    private WorkTypeResponse toResponse(WorkType workType) {
        return new WorkTypeResponse(workType.getId(), workType.getCode(), workType.getName(),
                workType.getServiceScope(), workType.getStageKind(), workType.isRequiresContractAmountBracket(),
                workType.getBasisDocumentDescription(), workType.isActive());
    }
}
