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
import uz.ithunter.crm.admin.dto.CreatePositionRequest;
import uz.ithunter.crm.admin.dto.PositionResponse;
import uz.ithunter.crm.admin.dto.UpdatePositionRequest;
import uz.ithunter.crm.shared.dto.PageResponse;

@RestController
@RequestMapping("/api/admin/positions")
public class AdminPositionController {

    private final AdminPositionService adminPositionService;

    public AdminPositionController(AdminPositionService adminPositionService) {
        this.adminPositionService = adminPositionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:VIEW')")
    public PageResponse<PositionResponse> list(Pageable pageable) {
        return adminPositionService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionResponse create(@Valid @RequestBody CreatePositionRequest request) {
        return adminPositionService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN:EDIT')")
    public PositionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePositionRequest request) {
        return adminPositionService.update(id, request);
    }
}
