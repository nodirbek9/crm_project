package uz.ithunter.crm.auth;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.auth.dto.CurrentUserResponse;
import uz.ithunter.crm.auth.dto.LoginRequest;
import uz.ithunter.crm.auth.dto.RefreshRequest;
import uz.ithunter.crm.auth.dto.TokenResponse;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;

/** API_SPEC.md 1. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final PermissionAuthorityResolver permissionAuthorityResolver;

    public AuthController(AuthenticationService authenticationService, UserRepository userRepository,
            PermissionAuthorityResolver permissionAuthorityResolver) {
        this.authenticationService = authenticationService;
        this.userRepository = userRepository;
        this.permissionAuthorityResolver = permissionAuthorityResolver;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authenticationService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Stateless JWT, no server-side refresh-token store (SECURITY_SPEC.md 10, [DEMO]) - the
        // client simply discards its tokens.
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal CustomUserPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("RESOURCE_NOT_FOUND", "User not found"));
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).map(Enum::name).toList();
        List<String> permissions = permissionAuthorityResolver.resolvePermissionCodes(user).stream().sorted().toList();

        return ResponseEntity.ok(new CurrentUserResponse(
                user.getId(), user.getEmail(), user.getFullName(), roleCodes, permissions,
                user.getDepartmentId(), user.getApplicantId()));
    }
}
