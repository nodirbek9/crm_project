package uz.ithunter.crm.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditEvent;
import uz.ithunter.crm.audit.AuditWriter;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.casemodule.CaseAccessPolicy;
import uz.ithunter.crm.casemodule.CaseItem;
import uz.ithunter.crm.casemodule.CaseMapper;
import uz.ithunter.crm.casemodule.CaseItemRepository;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageRepository;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.casemodule.ElectronicCaseRepository;
import uz.ithunter.crm.casemodule.dto.CaseResponse;
import uz.ithunter.crm.casemodule.engine.WorkflowEngine;
import uz.ithunter.crm.casemodule.engine.port.PaymentState;
import uz.ithunter.crm.finance.dto.ConfirmPaymentRequest;
import uz.ithunter.crm.finance.dto.ConfirmPriceRequest;
import uz.ithunter.crm.finance.dto.ContractResponse;
import uz.ithunter.crm.finance.dto.PaymentResponse;
import uz.ithunter.crm.finance.dto.PriceCalculationResponse;
import uz.ithunter.crm.finance.dto.RecordContractRequest;
import uz.ithunter.crm.finance.dto.SetPaymentStatusRequest;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.ConflictException;
import uz.ithunter.crm.shared.exception.FieldIssue;
import uz.ithunter.crm.shared.exception.IllegalStateTransitionException;
import uz.ithunter.crm.shared.exception.NotFoundException;
import uz.ithunter.crm.shared.exception.ValidationException;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowRepository;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;

/**
 * The application service for Phase 8 (API_SPEC.md 5, WORKFLOW_ENGINE_DESIGN.md's payment gate,
 * FIX 9/FIX 10). Same shape as {@link uz.ithunter.crm.casemodule.CaseService}: every public method
 * is one transaction, every mutation writes audit, every read/write goes through
 * {@link CaseAccessPolicy}'s two-layer check.
 *
 * <p>The demo tariffs are ASSUMPTIONS.md A3 - {@link PriceCalculator} does the arithmetic, this
 * class resolves which {@link PriceRule} rows apply and persists the result.
 */
@Service
public class AccountingService {

    private static final String DEMO_NOTICE =
            "Tariffs and pricing formula are DEMONSTRATION values (ASSUMPTIONS.md A3), not the client's real tariffs.";

    private final ElectronicCaseRepository electronicCaseRepository;
    private final CaseStageRepository caseStageRepository;
    private final CaseItemRepository caseItemRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStageRepository workflowStageRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final PriceCalculationRepository priceCalculationRepository;
    private final PriceCalculationLineRepository priceCalculationLineRepository;
    private final ContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentConfirmationRepository paymentConfirmationRepository;
    private final PriceCalculator priceCalculator;
    private final WorkflowEngine workflowEngine;
    private final CaseAccessPolicy caseAccessPolicy;
    private final CaseMapper caseMapper;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public AccountingService(ElectronicCaseRepository electronicCaseRepository,
            CaseStageRepository caseStageRepository,
            CaseItemRepository caseItemRepository,
            WorkflowRepository workflowRepository,
            WorkflowStageRepository workflowStageRepository,
            PriceRuleRepository priceRuleRepository,
            PriceCalculationRepository priceCalculationRepository,
            PriceCalculationLineRepository priceCalculationLineRepository,
            ContractRepository contractRepository,
            PaymentRepository paymentRepository,
            PaymentConfirmationRepository paymentConfirmationRepository,
            PriceCalculator priceCalculator,
            WorkflowEngine workflowEngine,
            CaseAccessPolicy caseAccessPolicy,
            CaseMapper caseMapper,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.electronicCaseRepository = electronicCaseRepository;
        this.caseStageRepository = caseStageRepository;
        this.caseItemRepository = caseItemRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStageRepository = workflowStageRepository;
        this.priceRuleRepository = priceRuleRepository;
        this.priceCalculationRepository = priceCalculationRepository;
        this.priceCalculationLineRepository = priceCalculationLineRepository;
        this.contractRepository = contractRepository;
        this.paymentRepository = paymentRepository;
        this.paymentConfirmationRepository = paymentConfirmationRepository;
        this.priceCalculator = priceCalculator;
        this.workflowEngine = workflowEngine;
        this.caseAccessPolicy = caseAccessPolicy;
        this.caseMapper = caseMapper;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }

    // ---- processing mode (API_SPEC.md 5, spec 1.9, 4.3, 15.4; FIX 9) ----

    @Transactional
    public CaseResponse setProcessingMode(UUID caseId, ProcessingMode mode, CustomUserPrincipal principal) {
        // Belt-and-braces: @PreAuthorize("hasAuthority('FINANCE:EDIT')") already blocks every
        // applicant at the controller (no role holds both APPLICANT and FINANCE:EDIT in the seeded
        // matrix), but S-03 wants this exact code, and CaseAccessPolicy.requireStaff established the
        // precedent of checking twice rather than trusting the permission gate alone.
        if (caseAccessPolicy.isApplicant(principal)) {
            throw new AccessDeniedDomainException("APPLICANT_CANNOT_SET_MODE",
                    "The applicant never chooses the processing mode");
        }
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        requireAccountingStage(electronicCase);

        Workflow workflow = requireWorkflow(electronicCase);
        if (mode == ProcessingMode.EXPEDITED && !workflow.isExpeditedAllowed()) {
            throw new IllegalStateTransitionException("EXPEDITED_NOT_ALLOWED",
                    "This route does not allow expedited processing");
        }

        ProcessingMode previousMode = electronicCase.getProcessingMode();
        electronicCase.setProcessingMode(mode);
        electronicCase.setProcessingModeSetById(principal.userId());
        electronicCase.setProcessingModeSetAt(Instant.now());
        electronicCaseRepository.save(electronicCase);

        auditWriter.write(AuditEvent.forCase(AuditAction.PROCESSING_MODE_SET, "ElectronicCase",
                electronicCase.getId(), caseId,
                previousMode == null ? null : Map.of("processingMode", previousMode.name()),
                Map.of("processingMode", mode.name()), null));

        // FIX 9: changing the mode BEFORE confirmation auto-recalculates.
        if (mode != previousMode) {
            priceCalculationRepository.findFirstByCaseIdAndStatusIn(caseId, List.of(PriceCalculationStatus.ACTIVE))
                    .ifPresent(active -> doCalculate(electronicCase, mode, PriceCalculationTrigger.MODE_CHANGED,
                            principal, active));
        }

        return caseMapper.toResponse(electronicCase);
    }

    // ---- price calculation (API_SPEC.md 5, spec 12.2, 12.3, 12.6; FIX 9) ----

    @Transactional
    public PriceCalculationResponse calculatePrice(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        requireAccountingStage(electronicCase);

        ProcessingMode mode = electronicCase.getProcessingMode() == null
                ? ProcessingMode.TRADITIONAL : electronicCase.getProcessingMode();
        var existingActive = priceCalculationRepository
                .findFirstByCaseIdAndStatusIn(caseId, List.of(PriceCalculationStatus.ACTIVE));
        PriceCalculationTrigger trigger = existingActive.isPresent()
                ? PriceCalculationTrigger.MANUAL_RECALC : PriceCalculationTrigger.INITIAL;

        PriceCalculation calculation = doCalculate(electronicCase, mode, trigger, principal, existingActive.orElse(null));
        return toResponse(calculation);
    }

    @Transactional(readOnly = true)
    public PriceCalculationResponse getPrice(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        PriceCalculation live = priceCalculationRepository
                .findFirstByCaseIdAndStatusIn(caseId,
                        List.of(PriceCalculationStatus.ACTIVE, PriceCalculationStatus.CONFIRMED))
                .orElseThrow(() -> new NotFoundException("No price has been calculated for this case yet"));
        return toResponse(live);
    }

    /** Shared by {@link #setProcessingMode} and {@link #calculatePrice} - one calculation, one audit row. */
    private PriceCalculation doCalculate(ElectronicCase electronicCase, ProcessingMode mode,
            PriceCalculationTrigger trigger, CustomUserPrincipal principal, PriceCalculation supersedes) {
        UUID caseId = electronicCase.getId();
        List<CaseItem> items = caseItemRepository.findByCaseIdOrderByLineNoAsc(caseId);
        List<PriceRule> rules = applicableRules(electronicCase.getServiceId(), Instant.now());
        PriceCalculator.PriceCalculationResult result = priceCalculator.calculate(items, mode, rules);

        if (supersedes != null) {
            supersedes.setStatus(PriceCalculationStatus.SUPERSEDED);
            priceCalculationRepository.save(supersedes);
        }

        PriceCalculation calculation = new PriceCalculation();
        calculation.setCaseId(caseId);
        calculation.setCalculationNo(priceCalculationRepository.countByCaseId(caseId) + 1);
        calculation.setProcessingMode(mode);
        calculation.setCalculatedTotal(result.total());
        calculation.setTriggerReason(trigger);
        calculation.setStatus(PriceCalculationStatus.ACTIVE);
        calculation.setSupersedesId(supersedes == null ? null : supersedes.getId());
        calculation.setCalculatedAt(Instant.now());
        calculation.setCalculatedById(principal == null ? null : principal.userId());
        calculation.setRuleSetSnapshot(objectMapper.writeValueAsString(
                rules.stream().map(PriceRule::getId).toList()));
        calculation = priceCalculationRepository.save(calculation);

        List<PriceCalculationLine> lines = new ArrayList<>();
        for (PriceCalculator.PriceCalculationResult.Line line : result.lines()) {
            PriceCalculationLine entity = new PriceCalculationLine();
            entity.setPriceCalculationId(calculation.getId());
            entity.setLineNo(line.lineNo());
            entity.setCaseItemId(line.caseItemId());
            entity.setPriceRuleId(line.priceRuleId());
            entity.setDescription(line.description());
            entity.setQuantity(line.quantity());
            entity.setUnitPrice(line.unitPrice());
            entity.setCoefficient(line.coefficient());
            entity.setLineTotal(line.lineTotal());
            lines.add(entity);
        }
        priceCalculationLineRepository.saveAll(lines);

        AuditAction action = trigger == PriceCalculationTrigger.INITIAL
                ? AuditAction.PRICE_CALCULATED : AuditAction.PRICE_RECALCULATED;
        auditWriter.write(AuditEvent.forCase(action, "PriceCalculation", calculation.getId(), caseId, null,
                Map.of("calculationNo", calculation.getCalculationNo(), "total", result.total().toString(),
                        "trigger", trigger.name()),
                null));
        return calculation;
    }

    private List<PriceRule> applicableRules(UUID serviceId, Instant now) {
        return priceRuleRepository.findByServiceIdAndActive(serviceId, true, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(rule -> !rule.getValidFrom().isAfter(now))
                .filter(rule -> rule.getValidTo() == null || rule.getValidTo().isAfter(now))
                .toList();
    }

    // ---- price confirmation -> creates the Contract (API_SPEC.md 5, spec 12.4; FIX 9; test I-12) ----

    @Transactional
    public ContractResponse confirmPrice(UUID caseId, ConfirmPriceRequest request, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        requireAccountingStage(electronicCase);

        PriceCalculation active = priceCalculationRepository
                .findFirstByCaseIdAndStatusIn(caseId, List.of(PriceCalculationStatus.ACTIVE))
                .orElseThrow(() -> new IllegalStateTransitionException("PRICE_NOT_CALCULATED",
                        "Calculate a price before confirming it"));

        BigDecimal calculatedAmount = active.getCalculatedTotal();
        BigDecimal actualAmount = request.actualAmount() != null ? request.actualAmount() : calculatedAmount;
        boolean overridden = actualAmount.compareTo(calculatedAmount) != 0;
        if (overridden && (request.reason() == null || request.reason().isBlank())) {
            throw new ValidationException("VALIDATION_FAILED",
                    "reason is required when actualAmount differs from the calculated total",
                    List.of(new FieldIssue("reason", "must not be blank when overriding the price")));
        }

        active.setStatus(PriceCalculationStatus.CONFIRMED);
        priceCalculationRepository.save(active);

        Contract contract = contractRepository.findByCaseId(caseId).orElseGet(Contract::new);
        contract.setCaseId(caseId);
        contract.setCalculatedAmount(calculatedAmount);
        contract.setActualAmount(actualAmount);
        contract.setCurrency(active.getCurrency());
        if (overridden) {
            contract.setAmountChangedById(principal.userId());
            contract.setAmountChangedAt(Instant.now());
            contract.setAmountChangeReason(request.reason());
        }
        contract = contractRepository.save(contract);

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("calculatedAmount", calculatedAmount.toString());
        newValue.put("actualAmount", actualAmount.toString());
        auditWriter.write(AuditEvent.forCase(AuditAction.PRICE_CONFIRMED, "Contract", contract.getId(), caseId,
                null, newValue, null));
        if (overridden) {
            auditWriter.write(AuditEvent.forCase(AuditAction.PRICE_CHANGED, "Contract", contract.getId(), caseId,
                    Map.of("calculatedAmount", calculatedAmount.toString()),
                    Map.of("actualAmount", actualAmount.toString()), request.reason()));
        }
        return toResponse(contract);
    }

    // ---- contract recording (API_SPEC.md 5, spec 12.5, 12.10; test I-13) ----

    /**
     * Fills in the sent contract's identifying fields and completes the ACCOUNTING stage - the
     * engine then activates PAYMENT_CONTROL (its activator stamps {@code paymentDueAt}, but
     * deliberately does not create the {@link Payment} row - see its javadoc), which this method
     * does immediately after, in the same transaction.
     */
    @Transactional
    public ContractResponse recordContract(UUID caseId, RecordContractRequest request, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        Contract contract = contractRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalStateTransitionException("PRICE_NOT_CONFIRMED",
                        "Confirm the price before recording the contract"));

        contract.setContractNumber(request.contractNumber());
        contract.setContractDate(request.contractDate());
        contract.setSentChannel(request.sentChannel());
        contract.setSentAt(request.sentAt());
        contract.setInvoiceReference(request.invoiceReference());
        contract.setInvoiceDate(request.invoiceDate());
        contract.setSent(true);
        contract = contractRepository.save(contract);

        auditWriter.write(AuditEvent.forCase(AuditAction.CONTRACT_RECORDED, "Contract", contract.getId(), caseId,
                null, Map.of("contractNumber", request.contractNumber()), null));
        auditWriter.write(AuditEvent.forCase(AuditAction.CONTRACT_SENT, "Contract", contract.getId(), caseId,
                null, Map.of("sentChannel", request.sentChannel().name(), "sentAt", request.sentAt().toString()),
                null));

        CaseStage accountingStage = findActiveStageByType(caseId, StageType.ACCOUNTING);
        if (accountingStage != null) {
            workflowEngine.completeStage(caseId, accountingStage.getWorkflowStageId());
            electronicCase = requireCase(caseId); // re-read: the engine may have mutated its status/paymentDueAt
            createPaymentIfAbsent(electronicCase, contract);
        }
        return toResponse(contract);
    }

    private void createPaymentIfAbsent(ElectronicCase electronicCase, Contract contract) {
        if (paymentRepository.findByCaseId(electronicCase.getId()).isPresent()) {
            return;
        }
        Payment payment = new Payment();
        payment.setCaseId(electronicCase.getId());
        payment.setContractId(contract.getId());
        payment.setStatus(PaymentStatus.WAITING_PAYMENT);
        BigDecimal contractAmount = contract.getActualAmount() != null
                ? contract.getActualAmount() : contract.getCalculatedAmount();
        payment.setContractAmount(contractAmount);
        payment.setConfirmedAmount(BigDecimal.ZERO);
        payment.setDebtAmount(contractAmount);
        payment.setWaitingSince(Instant.now());
        payment.setDueAt(electronicCase.getPaymentDueAt());
        paymentRepository.save(payment);
    }

    // ---- payment (API_SPEC.md 5, spec 12.7-12.9; tests I-14, I-15) ----

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID caseId, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        Payment payment = requirePayment(caseId);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse confirmPayment(UUID caseId, ConfirmPaymentRequest request, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        Payment payment = requirePayment(caseId);

        if (request.externalReference() != null && !request.externalReference().isBlank()
                && paymentConfirmationRepository
                        .findByPaymentIdAndExternalReference(payment.getId(), request.externalReference())
                        .isPresent()) {
            // Idempotent replay of an already-recorded confirmation (double-clicked submit).
            return toResponse(payment);
        }

        BigDecimal newConfirmed = payment.getConfirmedAmount().add(request.amount());
        if (newConfirmed.compareTo(payment.getContractAmount()) > 0) {
            throw new IllegalStateTransitionException("PAYMENT_EXCEEDS_CONTRACT",
                    "Confirming " + request.amount() + " would exceed the contract amount");
        }

        PaymentConfirmation confirmation = new PaymentConfirmation();
        confirmation.setPaymentId(payment.getId());
        confirmation.setAmount(request.amount());
        confirmation.setConfirmedById(principal.userId());
        confirmation.setConfirmedAt(Instant.now());
        confirmation.setNote(request.note());
        confirmation.setExternalReference(request.externalReference());
        paymentConfirmationRepository.save(confirmation);

        payment.setConfirmedAmount(newConfirmed);
        payment.setDebtAmount(payment.getContractAmount().subtract(newConfirmed));
        if (newConfirmed.compareTo(payment.getContractAmount()) == 0) {
            payment.setStatus(PaymentStatus.PAID);
        } else if (newConfirmed.compareTo(BigDecimal.ZERO) > 0) {
            payment.setStatus(PaymentStatus.PARTIALLY_PAID);
        }
        payment = paymentRepository.save(payment);

        auditWriter.write(AuditEvent.forCase(AuditAction.PAYMENT_CONFIRMED, "Payment", payment.getId(), caseId,
                null, Map.of("amount", request.amount().toString(), "status", payment.getStatus().name()), null));

        advanceIfPaymentGateOpen(electronicCase, payment);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse setPaymentStatus(UUID caseId, SetPaymentStatusRequest request, CustomUserPrincipal principal) {
        ElectronicCase electronicCase = requireCase(caseId);
        caseAccessPolicy.requireCanView(principal, electronicCase);
        Payment payment = requirePayment(caseId);

        PaymentStatus previous = payment.getStatus();
        payment.setStatus(request.status());
        payment = paymentRepository.save(payment);

        auditWriter.write(AuditEvent.forCase(AuditAction.PAYMENT_STATUS_CHANGED, "Payment", payment.getId(), caseId,
                Map.of("status", previous.name()), Map.of("status", request.status().name()), request.note()));

        if (request.status() == PaymentStatus.PAID) {
            advanceIfPaymentGateOpen(electronicCase, payment);
        }
        return toResponse(payment);
    }

    /**
     * The same gate {@code PaymentStateSatisfiedConditionHandler} evaluates (spec 12.8):
     * fully paid, or partially paid on a route that allows early execution. Only when it is open
     * does this human-triggered write complete PAYMENT_CONTROL - unlike the primary-check ACCEPTED
     * decision, payment confirmation does not always satisfy its own gate (I-15: a partial payment
     * must leave the case waiting, not advance it).
     */
    private void advanceIfPaymentGateOpen(ElectronicCase electronicCase, Payment payment) {
        Workflow workflow = requireWorkflow(electronicCase);
        PaymentState state = new PaymentState(payment.getStatus() == PaymentStatus.PAID,
                payment.getStatus() == PaymentStatus.PARTIALLY_PAID);
        boolean gateOpen = state.fullyPaid()
                || (workflow.isAllowExecutionBeforeFullPayment() && state.partiallyPaid());
        if (!gateOpen) {
            return;
        }
        CaseStage paymentControlStage = findActiveStageByType(electronicCase.getId(), StageType.PAYMENT_CONTROL);
        if (paymentControlStage != null) {
            workflowEngine.completeStage(electronicCase.getId(), paymentControlStage.getWorkflowStageId());
        }
    }

    // ---- private helpers ----

    private ElectronicCase requireCase(UUID caseId) {
        return electronicCaseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Case not found"));
    }

    private Workflow requireWorkflow(ElectronicCase electronicCase) {
        return workflowRepository.findById(electronicCase.getWorkflowId())
                .orElseThrow(() -> new NotFoundException("WORKFLOW_NOT_FOUND", "Pinned workflow no longer exists"));
    }

    private Payment requirePayment(UUID caseId) {
        return paymentRepository.findByCaseId(caseId)
                .orElseThrow(() -> new NotFoundException("No payment record exists for this case yet"));
    }

    private void requireAccountingStage(ElectronicCase electronicCase) {
        if (electronicCase.getStatus() != CaseStatus.IN_ACCOUNTING) {
            throw new IllegalStateTransitionException("INVALID_STATE_TRANSITION",
                    "Accounting actions require status IN_ACCOUNTING, but is " + electronicCase.getStatus());
        }
    }

    private CaseStage findActiveStageByType(UUID caseId, StageType stageType) {
        List<CaseStage> activeStages = caseStageRepository.findByCaseIdAndStatus(caseId, CaseStageStatus.ACTIVE);
        for (CaseStage stage : activeStages) {
            WorkflowStage config = workflowStageRepository.findById(stage.getWorkflowStageId()).orElse(null);
            if (config != null && config.getStageType() == stageType) {
                return stage;
            }
        }
        return null;
    }

    private PriceCalculationResponse toResponse(PriceCalculation calculation) {
        List<PriceCalculationResponse.Line> lines = priceCalculationLineRepository
                .findByPriceCalculationIdOrderByLineNoAsc(calculation.getId()).stream()
                .map(line -> new PriceCalculationResponse.Line(line.getLineNo(), line.getDescription(),
                        line.getQuantity(), line.getUnitPrice(), line.getCoefficient(), line.getLineTotal(),
                        line.getCaseItemId()))
                .toList();
        List<PriceCalculationResponse.HistoryEntry> history = priceCalculationRepository
                .findByCaseIdOrderByCalculationNoDesc(calculation.getCaseId()).stream()
                .filter(entry -> !entry.getId().equals(calculation.getId()))
                .map(entry -> new PriceCalculationResponse.HistoryEntry(entry.getId(), entry.getCalculationNo(),
                        entry.getProcessingMode(), entry.getCalculatedTotal(), entry.getTriggerReason(),
                        entry.getStatus(), entry.getCalculatedAt()))
                .toList();
        return new PriceCalculationResponse(calculation.getId(), calculation.getCalculationNo(),
                calculation.getProcessingMode(), calculation.getCalculatedTotal(), calculation.getCurrency(),
                calculation.getTriggerReason(), calculation.getStatus(), calculation.getCalculatedAt(),
                lines, history, DEMO_NOTICE);
    }

    private ContractResponse toResponse(Contract contract) {
        return new ContractResponse(contract.getId(), contract.getCaseId(), contract.getContractNumber(),
                contract.getContractDate(), contract.getCalculatedAmount(), contract.getActualAmount(),
                contract.getAmountChangedById(), contract.getAmountChangedAt(), contract.getAmountChangeReason(),
                contract.getCurrency(), contract.isSent(), contract.getSentAt(), contract.getSentChannel(),
                contract.getInvoiceReference(), contract.getInvoiceDate(), contract.getVersion());
    }

    private PaymentResponse toResponse(Payment payment) {
        List<PaymentResponse.ConfirmationRef> confirmations = paymentConfirmationRepository
                .findByPaymentIdOrderByConfirmedAtAsc(payment.getId()).stream()
                .map(c -> new PaymentResponse.ConfirmationRef(c.getId(), c.getAmount(), c.getConfirmedById(),
                        c.getConfirmedAt(), c.getNote(), c.getExternalReference()))
                .toList();
        return new PaymentResponse(payment.getId(), payment.getCaseId(), payment.getStatus(),
                payment.getContractAmount(), payment.getConfirmedAmount(), payment.getDebtAmount(),
                payment.getWaitingSince(), payment.getDueAt(), payment.isOverdue(), confirmations,
                payment.getVersion());
    }
}
