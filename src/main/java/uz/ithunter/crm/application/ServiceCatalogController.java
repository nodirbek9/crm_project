package uz.ithunter.crm.application;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.application.dto.ServiceSummary;

/**
 * The service catalog an applicant picks from before {@code POST /api/applications} - gated only
 * by {@code isAuthenticated()} (SecurityConfig's default), not by a specific permission: which
 * services a certification body offers is not sensitive, and no role's grant matrix includes
 * {@code REFERENCE_DATA:VIEW} except ADMIN (SECURITY_SPEC.md 3), so the admin CRUD endpoint at
 * {@code /api/admin/services} was never reachable from the application-creation screen every
 * other role, including APPLICANT, actually uses. See {@link uz.ithunter.crm.application.dto.ServiceSummary}.
 */
@RestController
@RequestMapping("/api/services")
public class ServiceCatalogController {

    private final ServiceRepository serviceRepository;

    public ServiceCatalogController(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @GetMapping
    public List<ServiceSummary> list() {
        return serviceRepository.findByActive(true, Pageable.unpaged(Sort.by("name"))).stream()
                .map(s -> new ServiceSummary(s.getId(), s.getCode(), s.getName(),
                        s.getSubmissionChannels().stream().map(Enum::name).collect(Collectors.toSet())))
                .toList();
    }
}
