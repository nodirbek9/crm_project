package uz.ithunter.crm.application;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.application.dto.ApplicationResponse;
import uz.ithunter.crm.application.dto.ApplicationSummary;
import uz.ithunter.crm.application.dto.CreateApplicationRequest;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.CaseService;
import uz.ithunter.crm.casemodule.dto.CaseResponse;
import uz.ithunter.crm.casemodule.dto.RegisterApplicationRequest;
import uz.ithunter.crm.shared.dto.PageResponse;

/** API_SPEC.md 3, including {@code /register} (Phase 7). */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final CaseService caseService;

    public ApplicationController(ApplicationService applicationService, CaseService caseService) {
        this.applicationService = applicationService;
        this.caseService = caseService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('APPLICATION:CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return applicationService.create(request, principal);
    }

    @PostMapping("/{id}/submit")
    public ApplicationResponse submit(@PathVariable UUID id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return applicationService.submit(id, principal);
    }

    /**
     * Registers a SUBMITTED application: creates the electronic case, materialises stages,
     * activates the entry stage, and writes audit — all in one transaction (spec 1.4, I-01, I-02).
     *
     * <p>Returns {@code 201 Created} with {@code Location: /api/cases/{id}} (API_SPEC.md 3).
     */
    @PostMapping("/{id}/register")
    @PreAuthorize("hasAuthority('APPLICATION:EDIT')")
    public ResponseEntity<CaseResponse> register(@PathVariable UUID id,
            @Valid @RequestBody(required = false) RegisterApplicationRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        RegisterApplicationRequest body = request != null ? request : new RegisterApplicationRequest(null);
        CaseResponse caseResponse = caseService.register(id, body, principal);
        return ResponseEntity.created(URI.create("/api/cases/" + caseResponse.id()))
                .body(caseResponse);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APPLICATION:VIEW')")
    public PageResponse<ApplicationSummary> list(Pageable pageable, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return applicationService.list(pageable, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('APPLICATION:VIEW')")
    public ApplicationResponse get(@PathVariable UUID id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return applicationService.get(id, principal);
    }
}
