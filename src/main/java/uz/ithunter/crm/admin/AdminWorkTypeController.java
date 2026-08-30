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
import uz.ithunter.crm.admin.dto.CreateWorkTypeRequest;
import uz.ithunter.crm.admin.dto.UpdateWorkTypeRequest;
import uz.ithunter.crm.admin.dto.WorkTypeResponse;
import uz.ithunter.crm.shared.dto.PageResponse;

@RestController
@RequestMapping("/api/admin/work-types")
public class AdminWorkTypeController {

    private final AdminWorkTypeService adminWorkTypeService;

    public AdminWorkTypeController(AdminWorkTypeService adminWorkTypeService) {
        this.adminWorkTypeService = adminWorkTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REFERENCE_DATA:VIEW')")
    public PageResponse<WorkTypeResponse> list(Pageable pageable) {
        return adminWorkTypeService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('REFERENCE_DATA:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkTypeResponse create(@Valid @RequestBody CreateWorkTypeRequest request) {
        return adminWorkTypeService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('REFERENCE_DATA:EDIT')")
    public WorkTypeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkTypeRequest request) {
        return adminWorkTypeService.update(id, request);
    }
}
