package uz.ithunter.crm.admin;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.admin.dto.CreateUserRequest;
import uz.ithunter.crm.admin.dto.UpdateUserRequest;
import uz.ithunter.crm.admin.dto.UserResponse;
import uz.ithunter.crm.shared.dto.PageResponse;

/** API_SPEC.md 8: admin CRUD, gated by {@code USER_ADMIN} permissions. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:VIEW')")
    public PageResponse<UserResponse> list(Pageable pageable) {
        return adminUserService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_ADMIN:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return adminUserService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN:EDIT')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return adminUserService.update(id, request);
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasAuthority('USER_ADMIN:BLOCK')")
    public ResponseEntity<UserResponse> block(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.block(id));
    }
}
