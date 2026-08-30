package uz.ithunter.crm.admin;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.admin.dto.CreateServiceRequest;
import uz.ithunter.crm.admin.dto.ServiceResponse;
import uz.ithunter.crm.admin.dto.UpdateServiceRequest;
import uz.ithunter.crm.shared.dto.PageResponse;

@RestController
@RequestMapping("/api/admin/services")
public class AdminServiceController {

    private final AdminServiceCatalogService adminServiceCatalogService;

    public AdminServiceController(AdminServiceCatalogService adminServiceCatalogService) {
        this.adminServiceCatalogService = adminServiceCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REFERENCE_DATA:VIEW')")
    public PageResponse<ServiceResponse> list(Pageable pageable) {
        return adminServiceCatalogService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('REFERENCE_DATA:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@Valid @RequestBody CreateServiceRequest request) {
        return adminServiceCatalogService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('REFERENCE_DATA:EDIT')")
    public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateServiceRequest request) {
        return adminServiceCatalogService.update(id, request);
    }
}
