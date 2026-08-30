package uz.ithunter.crm.applicant;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.applicant.dto.ApplicantResponse;
import uz.ithunter.crm.applicant.dto.CreateApplicantRequest;
import uz.ithunter.crm.applicant.dto.UpdateApplicantRequest;
import uz.ithunter.crm.auth.CustomUserPrincipal;

/** API_SPEC.md 2. {@code POST} is public - see ASSUMPTIONS.md A17. */
@RestController
@RequestMapping("/api/applicants")
public class ApplicantController {

    private final ApplicantService applicantService;

    public ApplicantController(ApplicantService applicantService) {
        this.applicantService = applicantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicantResponse create(@Valid @RequestBody CreateApplicantRequest request) {
        return applicantService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('APPLICATION:VIEW')")
    public ApplicantResponse get(@PathVariable UUID id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return applicantService.get(id, principal);
    }

    @PatchMapping("/{id}")
    public ApplicantResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateApplicantRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return applicantService.update(id, request, principal);
    }
}
