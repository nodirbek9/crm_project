package uz.ithunter.crm.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.casemodule.AbstractCaseIntegrationTest;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * Phase 8 DoD (FINAL_IMPLEMENTATION_ORDER.md): U-05 … U-08, U-11, U-12, I-10 … I-15, S-03, S-04.
 * U-05…U-08 live in {@link PriceCalculatorTest} (dependency-free); U-11/U-12 live in
 * {@code PaymentStateSatisfiedConditionHandlerTest} (Phase 7's engine package). This class covers
 * the HTTP-level integration rows.
 */
class AccountingFlowIntegrationTest extends AbstractCaseIntegrationTest {

    @Autowired
    private PriceRuleRepository priceRuleRepository;

    @Autowired
    private PriceCalculationRepository priceCalculationRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentWaitingScheduler paymentWaitingScheduler;

    private record Route(Workflow workflow, WorkflowStage primaryCheck, WorkflowStage accounting,
            WorkflowStage paymentControl, WorkflowStage completion) {
    }

    /** PRIMARY_CHECK -> ACCOUNTING -> PAYMENT_CONTROL -> COMPLETION, the last gated on payment. */
    private Route seedAccountingRoute(uz.ithunter.crm.application.Service service, Department department,
            boolean expeditedAllowed, boolean allowExecutionBeforeFullPayment) {
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 30);
        workflow.setExpeditedAllowed(expeditedAllowed);
        workflow.setAllowExecutionBeforeFullPayment(allowExecutionBeforeFullPayment);
        workflow = workflowRepository.save(workflow);

        WorkflowStage primaryCheck = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage accounting = seedStage(workflow, "ACC", StageType.ACCOUNTING, 2,
                "CONTRACT_SENT", department.getId(), null, true);
        WorkflowStage paymentControl = seedStage(workflow, "PAY", StageType.PAYMENT_CONTROL, 3,
                "AWAITING_PAYMENT", department.getId(), null, true);
        WorkflowStage completion = seedStage(workflow, "DONE", StageType.COMPLETION, 4,
                "RESULT_ISSUED", department.getId(), null, true);

        seedTransition(workflow, null, primaryCheck.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, primaryCheck.getId(), accounting.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, accounting.getId(), paymentControl.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, paymentControl.getId(), completion.getId(),
                ConditionType.PAYMENT_STATE_SATISFIED, null, 1);
        return new Route(workflow, primaryCheck, accounting, paymentControl, completion);
    }

    /** BASE_PER_ITEM + both mode coefficients, active and valid now, for the given service. */
    private void seedPriceRules(UUID serviceId) {
        priceRuleRepository.save(rule(serviceId, PriceRuleType.BASE_PER_ITEM, null,
                new BigDecimal("100000"), null));
        priceRuleRepository.save(rule(serviceId, PriceRuleType.MODE_COEFFICIENT, ProcessingMode.TRADITIONAL,
                null, new BigDecimal("1.0")));
        priceRuleRepository.save(rule(serviceId, PriceRuleType.MODE_COEFFICIENT, ProcessingMode.EXPEDITED,
                null, new BigDecimal("1.5")));
    }

    private PriceRule rule(UUID serviceId, PriceRuleType type, ProcessingMode mode,
            BigDecimal basePrice, BigDecimal coefficient) {
        PriceRule rule = new PriceRule();
        rule.setServiceId(serviceId);
        rule.setRuleType(type);
        rule.setProcessingMode(mode);
        rule.setBasePrice(basePrice);
        rule.setCoefficient(coefficient);
        rule.setValidFrom(Instant.now().minusSeconds(3600));
        rule.setActive(true);
        return rule;
    }

    private List<Map<String, Object>> twoItems() {
        return List.of(
                Map.of("name", "Item A", "quantity", 1, "unit", "PCS"),
                Map.of("name", "Item B", "quantity", 2, "unit", "PCS"));
    }

    /** Registers, drives primary check to ACCEPTED, leaving the case IN_ACCOUNTING with ACC active. */
    private UUID registerAndAcceptPrimaryCheck(User departmentHead, User specialist,
            uz.ithunter.crm.application.Service service, Applicant applicant, User applicantUser) throws Exception {
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), twoItems());
        String staffToken = token(departmentHead);
        var result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        UUID caseId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        String specialistToken = token(specialist);
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());
        return caseId;
    }

    // ---- I-10: accountant sets EXPEDITED; case records who and when ----

    @Test
    void accountantSetsExpeditedModeAndCaseRecordsWhoAndWhen() throws Exception {
        Department department = seedDepartment("I10");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false);
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);

        String accountantToken = token(accountant);
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/processing-mode")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "EXPEDITED"))))
                .andExpect(status().isOk());

        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getProcessingMode()).isEqualTo(ProcessingMode.EXPEDITED);
        assertThat(electronicCase.getProcessingModeSetById()).isEqualTo(accountant.getId());
        assertThat(electronicCase.getProcessingModeSetAt()).isNotNull();
    }

    // ---- S-04: expedited on a route that forbids it -> 422 ----

    @Test
    void expeditedOnARouteThatForbidsItIsRejected() throws Exception {
        Department department = seedDepartment("S04");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, false, false); // expeditedAllowed = false
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);

        String accountantToken = token(accountant);
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/processing-mode")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "EXPEDITED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- S-03: the applicant never chooses the mode ----

    @Test
    void applicantCannotSetProcessingMode() throws Exception {
        Department department = seedDepartment("S03");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false);
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);

        // Grant FINANCE:EDIT to APPLICANT temporarily so the request reaches AccountingService and
        // exercises its own explicit check, not just the @PreAuthorize layer that would normally
        // stop an applicant earlier with a generic PERMISSION_DENIED.
        grant(RoleCode.APPLICANT, "FINANCE:EDIT");
        String applicantToken = token(applicantUser);
        String body = mockMvc.perform(post("/api/accounting/cases/" + caseId + "/processing-mode")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "EXPEDITED"))))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("APPLICANT_CANNOT_SET_MODE");
    }

    // ---- I-11: price is calculated with one line per item and a stored rule snapshot ----

    @Test
    void priceIsCalculatedWithOneLinePerItemAndAStoredSnapshot() throws Exception {
        Department department = seedDepartment("I11");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false);
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);

        String accountantToken = token(accountant);
        String responseJson = mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/calculate")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> response = objectMapper.readValue(responseJson, Map.class);
        List<?> lines = (List<?>) response.get("lines");
        assertThat(lines).hasSize(2); // two case items

        var live = priceCalculationRepository
                .findFirstByCaseIdAndStatusIn(caseId, List.of(PriceCalculationStatus.ACTIVE)).orElseThrow();
        assertThat(live.getRuleSetSnapshot()).isNotBlank().isNotEqualTo("[]");
    }

    // ---- I-12: overriding the actual amount preserves calculated_amount, requires a reason ----

    @Test
    void confirmingADifferentActualAmountPreservesCalculatedAndRequiresAReason() throws Exception {
        Department department = seedDepartment("I12");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false);
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);
        String accountantToken = token(accountant);
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/calculate")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk());

        // Missing reason with a different amount -> 400.
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actualAmount", "250000.00"))))
                .andExpect(status().isBadRequest());

        String confirmedJson = mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("actualAmount", "250000.00", "reason", "Negotiated discount"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> contract = objectMapper.readValue(confirmedJson, Map.class);
        assertThat(new BigDecimal(contract.get("calculatedAmount").toString()))
                .isEqualByComparingTo("300000.00"); // 100000*1 + 100000*2, unchanged
        assertThat(new BigDecimal(contract.get("actualAmount").toString())).isEqualByComparingTo("250000.00");
        assertThat(contract.get("amountChangedById")).isEqualTo(accountant.getId().toString());
        assertThat(contract.get("amountChangedAt")).isNotNull();
    }

    // ---- I-13: contract recording requires number, date, channel and sent_at together ----

    @Test
    void contractRecordingRequiresAllFourFieldsTogether() throws Exception {
        Department department = seedDepartment("I13");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false);
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);
        String accountantToken = token(accountant);
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/calculate")
                        .header("Authorization", "Bearer " + accountantToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isOk());

        // contractNumber missing -> 400 (bean validation).
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/contract")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractDate", "2026-08-29", "sentChannel", "DIDOX",
                                "sentAt", "2026-08-29T10:00:00Z"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/contract")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractNumber", "CTR-001", "contractDate", "2026-08-29",
                                "sentChannel", "DIDOX", "sentAt", "2026-08-29T10:00:00Z"))))
                .andExpect(status().isOk());

        var contract = contractRepository.findByCaseId(caseId).orElseThrow();
        assertThat(contract.isSent()).isTrue();
        assertThat(paymentRepository.findByCaseId(caseId)).isPresent();
    }

    // ---- I-14 / I-15: full vs partial payment ----

    @Test
    void fullPaymentConfirmationAdvancesTheCaseOutOfWaitingPayment() throws Exception {
        Department department = seedDepartment("I14");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false);
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);
        String accountantToken = token(accountant);
        calculateConfirmAndRecordContract(caseId, accountantToken);

        var payment = paymentRepository.findByCaseId(caseId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_PAYMENT);

        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/payment/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amount", payment.getContractAmount().toString()))))
                .andExpect(status().isOk());

        var updated = paymentRepository.findByCaseId(caseId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(updated.getDebtAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getStatus()).isNotEqualTo(CaseStatus.WAITING_PAYMENT);
    }

    @Test
    void partialPaymentKeepsDebtAndStatusPartiallyPaidWithoutAdvancing() throws Exception {
        Department department = seedDepartment("I15");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false); // early execution forbidden
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);
        String accountantToken = token(accountant);
        calculateConfirmAndRecordContract(caseId, accountantToken);

        var payment = paymentRepository.findByCaseId(caseId).orElseThrow();
        BigDecimal half = payment.getContractAmount().divide(new BigDecimal("2"));

        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/payment/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", half.toString()))))
                .andExpect(status().isOk());

        var updated = paymentRepository.findByCaseId(caseId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(updated.getDebtAmount()).isEqualByComparingTo(updated.getContractAmount().subtract(half));
        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getStatus()).isEqualTo(CaseStatus.WAITING_PAYMENT);
    }

    @Test
    void partialPaymentAdvancesTheCaseWhenTheRouteAllowsEarlyExecution() throws Exception {
        Department department = seedDepartment("I15B");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, true); // early execution ALLOWED
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);
        String accountantToken = token(accountant);
        calculateConfirmAndRecordContract(caseId, accountantToken);

        var payment = paymentRepository.findByCaseId(caseId).orElseThrow();
        BigDecimal half = payment.getContractAmount().divide(new BigDecimal("2"));
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/payment/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", half.toString()))))
                .andExpect(status().isOk());

        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getStatus()).isNotEqualTo(CaseStatus.WAITING_PAYMENT);
    }

    private void calculateConfirmAndRecordContract(UUID caseId, String accountantToken) throws Exception {
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/calculate")
                        .header("Authorization", "Bearer " + accountantToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/contract")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractNumber", "CTR-" + unique(), "contractDate", "2026-08-29",
                                "sentChannel", "DIDOX", "sentAt", "2026-08-29T10:00:00Z"))))
                .andExpect(status().isOk());
    }

    // ---- bonus: the scheduler flags overdue without ever changing status/rejecting (spec 12.9) ----

    @Test
    void schedulerFlagsOverduePaymentWithoutRejectingTheCase() throws Exception {
        Department department = seedDepartment("SCHED");
        uz.ithunter.crm.application.Service service = seedService();
        seedAccountingRoute(service, department, true, false);
        seedPriceRules(service.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID caseId = registerAndAcceptPrimaryCheck(departmentHead, specialist, service, applicant, applicantUser);
        calculateConfirmAndRecordContract(caseId, token(accountant));

        int flagged = paymentWaitingScheduler.sweep(Instant.now().plusSeconds(60L * 60 * 24 * 30));

        assertThat(flagged).isGreaterThanOrEqualTo(1);
        var payment = paymentRepository.findByCaseId(caseId).orElseThrow();
        assertThat(payment.isOverdue()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_PAYMENT); // never auto-rejected
        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.isPaymentOverdue()).isTrue();
        assertThat(electronicCase.getStatus()).isEqualTo(CaseStatus.WAITING_PAYMENT); // not rejected
    }
}
