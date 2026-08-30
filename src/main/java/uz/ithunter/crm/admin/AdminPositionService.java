package uz.ithunter.crm.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.admin.dto.CreatePositionRequest;
import uz.ithunter.crm.admin.dto.PositionResponse;
import uz.ithunter.crm.admin.dto.UpdatePositionRequest;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.user.Position;
import uz.ithunter.crm.user.PositionRepository;

/** Spec 16.13: position carries no permissions - this service never touches grants. */
@Service
public class AdminPositionService {

    private final PositionRepository positionRepository;
    private final AuditWriter auditWriter;

    public AdminPositionService(PositionRepository positionRepository, AuditWriter auditWriter) {
        this.positionRepository = positionRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public PageResponse<PositionResponse> list(Pageable pageable) {
        return PageResponse.of(positionRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public PositionResponse create(CreatePositionRequest request) {
        Position position = new Position();
        position.setCode(request.code());
        position.setName(request.name());
        position.setActive(true);
        position = positionRepository.save(position);

        auditWriter.write(AuditEvent.administrative(AuditAction.POSITION_CHANGED, "Position",
                position.getId(), null, snapshot(position)));
        return toResponse(position);
    }

    @Transactional
    public PositionResponse update(UUID id, UpdatePositionRequest request) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Position not found"));
        Map<String, Object> oldValue = snapshot(position);

        position.setName(request.name());
        position.setActive(request.active());
        position = positionRepository.save(position);

        auditWriter.write(AuditEvent.administrative(AuditAction.POSITION_CHANGED, "Position",
                position.getId(), oldValue, snapshot(position)));
        return toResponse(position);
    }

    private Map<String, Object> snapshot(Position position) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", position.getCode());
        map.put("name", position.getName());
        map.put("active", position.isActive());
        return map;
    }

    private PositionResponse toResponse(Position position) {
        return new PositionResponse(position.getId(), position.getCode(), position.getName(), position.isActive());
    }
}
