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
import uz.ithunter.crm.admin.dto.CreateExternalStageRequest;
import uz.ithunter.crm.admin.dto.ExternalStageResponse;
import uz.ithunter.crm.admin.dto.UpdateExternalStageRequest;
import uz.ithunter.crm.shared.dto.PageResponse;

@RestController
@RequestMapping("/api/admin/external-stages")
public class AdminExternalStageController {

    private final AdminExternalStageService adminExternalStageService;

    public AdminExternalStageController(AdminExternalStageService adminExternalStageService) {
        this.adminExternalStageService = adminExternalStageService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REFERENCE_DATA:VIEW')")
    public PageResponse<ExternalStageResponse> list(Pageable pageable) {
        return adminExternalStageService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('REFERENCE_DATA:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalStageResponse create(@Valid @RequestBody CreateExternalStageRequest request) {
        return adminExternalStageService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('REFERENCE_DATA:EDIT')")
    public ExternalStageResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateExternalStageRequest request) {
        return adminExternalStageService.update(id, request);
    }
}
