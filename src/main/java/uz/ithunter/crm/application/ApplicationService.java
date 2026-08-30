package uz.ithunter.crm.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.application.dto.ApplicationResponse;
import uz.ithunter.crm.application.dto.ApplicationSummary;
import uz.ithunter.crm.application.dto.CreateApplicationRequest;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;

/**
 * API_SPEC.md 3, minus {@code /register} (Phase 7 - it needs {@code ElectronicCase}, which doesn't
 * exist yet). {@code create} implements **I-05** (TEST_MATRIX.md): a PAPER submission is rejected
 * without a resolvable registrar and accepted with one. ASSUMPTIONS.md A18 records why no staff
 * role is pre-granted {@code APPLICATION:CREATE} for the PAPER/SINGLE_WINDOW path - a deployment
 * grants it via Phase 4's admin endpoint if it wants one. A20 records why {@code list}/{@code get}
 * show every application to any staff member with {@code APPLICATION:VIEW} for now - there is no
 * department/case scoping to filter by until Phase 7.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;

    public ApplicationService(ApplicationRepository applicationRepository, ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request, CustomUserPrincipal principal) {
        UUID applicantId;
        UUID registeredById;

        if (principal.applicantId() != null) {
            applicantId = principal.applicantId();
            registeredById = null;
        } else {
            if (request.applicantId() == null) {
                throw new ValidationException("VALIDATION_FAILED", "applicantId is required when staff create an application on someone's behalf");
            }
            applicantId = request.applicantId();
            registeredById = principal.userId();
        }

        if (request.submissionChannel() == SubmissionChannel.PAPER && registeredById == null) {
            // I-05: PAPER without a resolvable registrar is rejected before it ever reaches the DB;
            // ck_application_paper_registrar is the backstop layer if this check were ever bypassed.
            throw new IllegalStateTransitionException("REQUIRED_DATA_MISSING",
                    "PAPER submissions require an authenticated staff member as the registrar");
        }

        Application application = new Application();
        application.setNumber(generateNumber());
        application.setApplicantId(applicantId);
        application.setServiceId(request.serviceId());
        application.setSubmissionChannel(request.submissionChannel());
        application.setRegisteredById(registeredById);
        application.setStatus(ApplicationStatus.DRAFT);
        application.setFormData(objectMapper.writeValueAsString(mergeFormData(request)));
        application = applicationRepository.save(application);

        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse submit(UUID id, CustomUserPrincipal principal) {
        Application application = findOrThrow(id, principal);
        if (principal.applicantId() == null || !principal.applicantId().equals(application.getApplicantId())) {
            throw new AccessDeniedDomainException("PERMISSION_DENIED", "Only the owning applicant may submit this application");
        }
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION",
                    "Only a DRAFT application can be submitted");
        }
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(Instant.now());
        application = applicationRepository.save(application);
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationSummary> list(Pageable pageable, CustomUserPrincipal principal) {
        Page<Application> page = principal.applicantId() != null
                ? applicationRepository.findByApplicantId(principal.applicantId(), pageable)
                : applicationRepository.findAll(pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID id, CustomUserPrincipal principal) {
        return toResponse(findOrThrow(id, principal));
    }

    private Application findOrThrow(UUID id, CustomUserPrincipal principal) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application not found"));
        if (principal.applicantId() != null && !principal.applicantId().equals(application.getApplicantId())) {
            throw new NotFoundException("Application not found");
        }
        return application;
    }

    private String generateNumber() {
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        long sequence = applicationRepository.count() + 1;
        return "APP-" + year + "-" + String.format("%06d", sequence);
    }

    private Map<String, Object> mergeFormData(CreateApplicationRequest request) {
        Map<String, Object> merged = new HashMap<>();
        if (request.formData() != null) {
            merged.putAll(request.formData());
        }
        merged.put("items", request.items() == null ? List.of() : request.items());
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFormData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, Map.class);
    }

    private ApplicationResponse toResponse(Application a) {
        return new ApplicationResponse(a.getId(), a.getNumber(), a.getApplicantId(), a.getServiceId(),
                a.getSubmissionChannel(), a.getRegisteredById(), a.getSubmittedAt(), a.getRegisteredAt(),
                a.getStatus().name(), parseFormData(a.getFormData()), a.getVersion(), a.getCreatedAt(),
                a.getUpdatedAt());
    }

    private ApplicationSummary toSummary(Application a) {
        return new ApplicationSummary(a.getId(), a.getNumber(), a.getApplicantId(), a.getServiceId(),
                a.getSubmissionChannel(), a.getStatus().name(), a.getSubmittedAt(), a.getRegisteredAt());
    }
}
