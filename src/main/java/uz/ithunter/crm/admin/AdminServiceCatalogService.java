package uz.ithunter.crm.admin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.admin.dto.CreateServiceRequest;
import uz.ithunter.crm.admin.dto.ServiceResponse;
import uz.ithunter.crm.admin.dto.UpdateServiceRequest;
import uz.ithunter.crm.application.SubmissionChannel;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;

/**
 * Admin CRUD for the {@code Service} catalog entity - named {@code AdminServiceCatalogService} to
 * avoid clashing with {@code uz.ithunter.crm.application.Service} (the domain entity) and Spring's
 * own {@code @Service} stereotype, both in scope in this one class.
 */
@Service
public class AdminServiceCatalogService {

    private final uz.ithunter.crm.application.ServiceRepository serviceRepository;
    private final AuditWriter auditWriter;

    public AdminServiceCatalogService(uz.ithunter.crm.application.ServiceRepository serviceRepository,
            AuditWriter auditWriter) {
        this.serviceRepository = serviceRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> list(Pageable pageable) {
        return PageResponse.of(serviceRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        uz.ithunter.crm.application.Service entity = new uz.ithunter.crm.application.Service();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(true);
        entity.setContractRequired(request.contractRequired());
        entity.setPaymentRequired(request.paymentRequired());
        entity.setStandaloneLaboratory(request.standaloneLaboratory());
        entity.setSubmissionChannels(resolveChannels(request.submissionChannels()));
        entity = serviceRepository.save(entity);

        auditWriter.write(AuditEvent.administrative(AuditAction.REFERENCE_DATA_CHANGED, "Service",
                entity.getId(), null, snapshot(entity)));
        return toResponse(entity);
    }

    @Transactional
    public ServiceResponse update(UUID id, UpdateServiceRequest request) {
        uz.ithunter.crm.application.Service entity = serviceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Service not found"));
        Map<String, Object> oldValue = snapshot(entity);

        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        entity.setContractRequired(request.contractRequired());
        entity.setPaymentRequired(request.paymentRequired());
        entity.setStandaloneLaboratory(request.standaloneLaboratory());
        entity.setSubmissionChannels(resolveChannels(request.submissionChannels()));
        entity = serviceRepository.save(entity);

        auditWriter.write(AuditEvent.administrative(AuditAction.REFERENCE_DATA_CHANGED, "Service",
                entity.getId(), oldValue, snapshot(entity)));
        return toResponse(entity);
    }

    private Set<SubmissionChannel> resolveChannels(Set<String> channels) {
        Set<SubmissionChannel> resolved = new LinkedHashSet<>();
        for (String channel : channels) {
            try {
                resolved.add(SubmissionChannel.valueOf(channel));
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("VALIDATION_FAILED", "Unknown submission channel: " + channel);
            }
        }
        return resolved;
    }

    private Map<String, Object> snapshot(uz.ithunter.crm.application.Service entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", entity.getCode());
        map.put("name", entity.getName());
        map.put("active", entity.isActive());
        map.put("contractRequired", entity.isContractRequired());
        map.put("paymentRequired", entity.isPaymentRequired());
        map.put("standaloneLaboratory", entity.isStandaloneLaboratory());
        map.put("submissionChannels", entity.getSubmissionChannels().stream().map(Enum::name).sorted().toList());
        return map;
    }

    private ServiceResponse toResponse(uz.ithunter.crm.application.Service entity) {
        return new ServiceResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                entity.isActive(), entity.isContractRequired(), entity.isPaymentRequired(),
                entity.isStandaloneLaboratory(),
                entity.getSubmissionChannels().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
