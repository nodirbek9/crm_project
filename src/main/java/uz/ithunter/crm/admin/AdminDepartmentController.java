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
import uz.ithunter.crm.admin.dto.CreateDepartmentRequest;
import uz.ithunter.crm.admin.dto.DepartmentResponse;
import uz.ithunter.crm.admin.dto.UpdateDepartmentRequest;
import uz.ithunter.crm.shared.dto.PageResponse;

@RestController
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;

    public AdminDepartmentController(AdminDepartmentService adminDepartmentService) {
        this.adminDepartmentService = adminDepartmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:VIEW')")
    public PageResponse<DepartmentResponse> list(Pageable pageable) {
        return adminDepartmentService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@Valid @RequestBody CreateDepartmentRequest request) {
        return adminDepartmentService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN:EDIT')")
    public DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return adminDepartmentService.update(id, request);
    }
}
