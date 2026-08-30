package uz.ithunter.crm.finance;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.dto.CaseResponse;
import uz.ithunter.crm.finance.dto.ConfirmPaymentRequest;
import uz.ithunter.crm.finance.dto.ConfirmPriceRequest;
import uz.ithunter.crm.finance.dto.ContractResponse;
import uz.ithunter.crm.finance.dto.PaymentResponse;
import uz.ithunter.crm.finance.dto.PriceCalculationResponse;
import uz.ithunter.crm.finance.dto.RecordContractRequest;
import uz.ithunter.crm.finance.dto.SetPaymentStatusRequest;
import uz.ithunter.crm.finance.dto.SetProcessingModeRequest;

/**
 * API_SPEC.md 5 - Accounting. Every endpoint carries layer-1 authorization via
 * {@code @PreAuthorize}; layer-2 (object-level) is enforced inside {@link AccountingService} via
 * {@link uz.ithunter.crm.casemodule.CaseAccessPolicy}.
 */
@RestController
@RequestMapping("/api/accounting/cases")
public class AccountingController {

    private final AccountingService accountingService;

    public AccountingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @PostMapping("/{id}/processing-mode")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public CaseResponse setProcessingMode(@PathVariable UUID id,
            @Valid @RequestBody SetProcessingModeRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.setProcessingMode(id, request.mode(), principal);
    }

    @PostMapping("/{id}/price/calculate")
    @PreAuthorize("hasAuthority('FINANCE:CREATE')")
    public PriceCalculationResponse calculatePrice(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.calculatePrice(id, principal);
    }

    @GetMapping("/{id}/price")
    @PreAuthorize("hasAuthority('FINANCE:VIEW')")
    public PriceCalculationResponse getPrice(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.getPrice(id, principal);
    }

    @PostMapping("/{id}/price/confirm")
    @PreAuthorize("hasAuthority('FINANCE:APPROVE')")
    public ContractResponse confirmPrice(@PathVariable UUID id,
            @Valid @RequestBody ConfirmPriceRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.confirmPrice(id, request, principal);
    }

    @PostMapping("/{id}/contract")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public ContractResponse recordContract(@PathVariable UUID id,
            @Valid @RequestBody RecordContractRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.recordContract(id, request, principal);
    }

    @GetMapping("/{id}/payment")
    @PreAuthorize("hasAuthority('FINANCE:VIEW')")
    public PaymentResponse getPayment(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.getPayment(id, principal);
    }

    @PostMapping("/{id}/payment/confirm")
    @PreAuthorize("hasAuthority('FINANCE:APPROVE')")
    public PaymentResponse confirmPayment(@PathVariable UUID id,
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.confirmPayment(id, request, principal);
    }

    @PostMapping("/{id}/payment/status")
    @PreAuthorize("hasAuthority('FINANCE:APPROVE')")
    public PaymentResponse setPaymentStatus(@PathVariable UUID id,
            @Valid @RequestBody SetPaymentStatusRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return accountingService.setPaymentStatus(id, request, principal);
    }
}
