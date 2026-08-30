package uz.ithunter.crm.casemodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.finance.PriceRule;
import uz.ithunter.crm.finance.PriceRuleRepository;
import uz.ithunter.crm.finance.PriceRuleType;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.task.TaskRepository;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;

/**
 * TEST_MATRIX.md section F, Phase 12 DoD. {@code WorkflowConcurrencyTest} already covers C-07
 * (workflow publish); this class covers C-01, C-02, C-03, C-06 - every one of them a genuine race
 * via {@code ExecutorService} + a {@code CountDownLatch}, per the pattern that file's own javadoc
 * documents, asserting the DB-level invariant rather than which thread happened to win.
 */
class ConcurrencyIntegrationTest extends AbstractCaseIntegrationTest {

    private ExecutorService twoThreads() {
        return Executors.newFixedThreadPool(2);
    }

    // =====================================================================================
    // C-01: two simultaneous completeTask calls on the SAME task - completes once, advances once
    // =====================================================================================

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkflowStageRepository workflowStageRepository;

    @Autowired
    private PriceRuleRepository priceRuleRepository;

    private record SingleExecFixture(UUID caseId, WorkflowStage done, User specialist) {
    }

    /** PC -> EXEC (single task, no result/approval needed) -> DONE. */
    private SingleExecFixture buildSingleExecCase(String prefix) throws Exception {
        Department department = seedDepartment(prefix);
        uz.ithunter.crm.application.Service service = seedService();
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 15);
        WorkflowStage pc = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage exec = seedStage(workflow, "EXEC", StageType.EXECUTION, 2,
                "WORK_IN_PROGRESS", department.getId(), null, true);
        WorkflowStage done = seedStage(workflow, "DONE", StageType.COMPLETION, 3,
                "RESULT_ISSUED", department.getId(), null, true);
        seedTransition(workflow, null, pc.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pc.getId(), exec.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, exec.getId(), done.getId(), ConditionType.ALWAYS, null, 1);

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        grant(RoleCode.DEPARTMENT_HEAD, "TASK:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        var result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        UUID caseId = UUID.fromString(result.getResponse().getHeader("Location").replace("/api/cases/", ""));

        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + token(specialist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());

        UUID taskId = taskRepository.findByCaseId(caseId).stream()
                .filter(t -> t.getWorkflowStageId().equals(exec.getId())).findFirst().orElseThrow().getId();
        mockMvc.perform(post("/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", specialist.getId()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + token(specialist)))
                .andExpect(status().isOk());

        return new SingleExecFixture(caseId, done, specialist);
    }

    @Test
    void twoSimultaneousCompleteTaskCallsCompleteTheTaskOnceAndAdvanceOnce() throws Exception {
        SingleExecFixture fx = buildSingleExecCase("C01");
        UUID taskId = taskRepository.findByCaseId(fx.caseId()).stream()
                .filter(t -> "IN_PROGRESS".equals(t.getStatus().name())).findFirst().orElseThrow().getId();
        String token = token(fx.specialist());
        int version = jdbc.queryForObject("SELECT version FROM task WHERE id = ?", Integer.class, taskId);
        String body = objectMapper.writeValueAsString(Map.of("version", version));

        ExecutorService executor = twoThreads();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> completeCall = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/tasks/" + taskId + "/complete")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> r1 = executor.submit(completeCall);
        Future<Integer> r2 = executor.submit(completeCall);
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        int s1 = r1.get(10, TimeUnit.SECONDS);
        int s2 = r2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Every response is either 200 (winner, or an idempotent replay) or 409 (lost the
        // optimistic-lock race) - never a 500.
        assertThat(List.of(s1, s2)).allMatch(s -> s == 200 || s == 409);

        assertThat(jdbc.queryForObject("SELECT status FROM task WHERE id = ?", String.class, taskId))
                .isEqualTo("COMPLETED");
        // AuditEvent.forCase() never populates the audit_log.task_id FK column (it stays null for
        // every case-scoped event) - TaskService.complete() writes entity_type='Task' with
        // entity_id=taskId instead, so that is what identifies this row, not task_id.
        Integer taskCompletedCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'Task' AND entity_id = ? "
                        + "AND action = 'TASK_COMPLETED'",
                Integer.class, taskId);
        assertThat(taskCompletedCount).isEqualTo(1);
        // COMPLETION-type stages close themselves immediately on activation (WorkflowEngine's own
        // activateStage javadoc), so DONE lands on COMPLETED, not ACTIVE, the instant it opens.
        assertThat(jdbc.queryForObject(
                "SELECT status FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?",
                String.class, fx.caseId(), fx.done().getId()))
                .isEqualTo("COMPLETED");
    }

    // =====================================================================================
    // C-02: two parallel siblings finishing simultaneously activate the join stage exactly once
    // =====================================================================================

    private record ParallelFixture(UUID caseId, WorkflowStage p1, WorkflowStage p2, WorkflowStage join,
            User specialist1, User specialist2) {
    }

    /** PC -> (P1, P2 in GROUP1, both required) -> JOIN. */
    private ParallelFixture buildParallelCase(String prefix) throws Exception {
        Department department = seedDepartment(prefix);
        uz.ithunter.crm.application.Service service = seedService();
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 15);
        WorkflowStage pc = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage p1 = seedStage(workflow, "P1", StageType.EXECUTION, 2,
                "WORK_IN_PROGRESS", department.getId(), "GROUP1", true);
        WorkflowStage p2 = seedStage(workflow, "P2", StageType.EXECUTION, 3,
                "LAB_TESTS_IN_PROGRESS", department.getId(), "GROUP1", true);
        WorkflowStage join = seedStage(workflow, "JOIN", StageType.FINAL_REVIEW, 4,
                "FINAL_DOCUMENT_PREPARING", department.getId(), null, true);
        seedTransition(workflow, null, pc.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pc.getId(), p1.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pc.getId(), p2.getId(), ConditionType.ALWAYS, null, 2);
        seedTransition(workflow, p1.getId(), join.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "GROUP1", 1);
        seedTransition(workflow, p2.getId(), join.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "GROUP1", 2);

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist1 = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User specialist2 = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        grant(RoleCode.DEPARTMENT_HEAD, "TASK:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        var result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        UUID caseId = UUID.fromString(result.getResponse().getHeader("Location").replace("/api/cases/", ""));

        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + token(specialist1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());

        UUID t1 = taskRepository.findByCaseId(caseId).stream()
                .filter(t -> t.getWorkflowStageId().equals(p1.getId())).findFirst().orElseThrow().getId();
        UUID t2 = taskRepository.findByCaseId(caseId).stream()
                .filter(t -> t.getWorkflowStageId().equals(p2.getId())).findFirst().orElseThrow().getId();
        assignAndStart(t1, departmentHead, specialist1);
        assignAndStart(t2, departmentHead, specialist2);

        return new ParallelFixture(caseId, p1, p2, join, specialist1, specialist2);
    }

    private void assignAndStart(UUID taskId, User assigner, User assignee) throws Exception {
        mockMvc.perform(post("/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + token(assigner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", assignee.getId()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + token(assignee)))
                .andExpect(status().isOk());
    }

    @Test
    void twoParallelSiblingsFinishingSimultaneouslyActivateTheJoinStageExactlyOnce() throws Exception {
        ParallelFixture fx = buildParallelCase("C02");
        UUID t1 = taskRepository.findByCaseId(fx.caseId()).stream()
                .filter(t -> t.getWorkflowStageId().equals(fx.p1().getId())).findFirst().orElseThrow().getId();
        UUID t2 = taskRepository.findByCaseId(fx.caseId()).stream()
                .filter(t -> t.getWorkflowStageId().equals(fx.p2().getId())).findFirst().orElseThrow().getId();

        ExecutorService executor = twoThreads();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> c1 = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/tasks/" + t1 + "/complete")
                            .header("Authorization", "Bearer " + token(fx.specialist1()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                    .andReturn().getResponse().getStatus();
        };
        Callable<Integer> c2 = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/tasks/" + t2 + "/complete")
                            .header("Authorization", "Bearer " + token(fx.specialist2()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> r1 = executor.submit(c1);
        Future<Integer> r2 = executor.submit(c2);
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        int s1 = r1.get(10, TimeUnit.SECONDS);
        int s2 = r2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(List.of(s1, s2)).allMatch(s -> s == 200);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?",
                String.class, fx.caseId(), fx.join().getId()))
                .isEqualTo("ACTIVE");
        Integer joinActivations = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE case_id = ? AND action = 'STAGE_ACTIVATED' "
                        + "AND entity_id = (SELECT id FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?)",
                Integer.class, fx.caseId(), fx.caseId(), fx.join().getId());
        assertThat(joinActivations).isEqualTo(1);
    }

    // =====================================================================================
    // C-03: two simultaneous confirmPayment with the same externalReference - never double-counted
    // =====================================================================================

    private record PaymentFixture(UUID caseId, BigDecimal contractAmount, User accountant) {
    }

    private PaymentFixture buildCaseReadyForPaymentConfirmation(String prefix) throws Exception {
        Department department = seedDepartment(prefix);
        uz.ithunter.crm.application.Service service = seedService();
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 30);
        WorkflowStage pc = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage acc = seedStage(workflow, "ACC", StageType.ACCOUNTING, 2,
                "CONTRACT_SENT", department.getId(), null, true);
        WorkflowStage pay = seedStage(workflow, "PAY", StageType.PAYMENT_CONTROL, 3,
                "AWAITING_PAYMENT", department.getId(), null, true);
        WorkflowStage done = seedStage(workflow, "DONE", StageType.COMPLETION, 4,
                "RESULT_ISSUED", department.getId(), null, true);
        seedTransition(workflow, null, pc.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pc.getId(), acc.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, acc.getId(), pay.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pay.getId(), done.getId(), ConditionType.PAYMENT_STATE_SATISFIED, null, 1);

        priceRuleRepository.save(rule(service.getId(), PriceRuleType.BASE_PER_ITEM, null,
                new BigDecimal("5000000"), null));
        priceRuleRepository.save(rule(service.getId(), PriceRuleType.MODE_COEFFICIENT,
                ProcessingMode.TRADITIONAL, null, new BigDecimal("1.0")));

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User accountant = seedStaffUser(RoleCode.ACCOUNTANT, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        var result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        UUID caseId = UUID.fromString(result.getResponse().getHeader("Location").replace("/api/cases/", ""));

        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + token(specialist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());

        String accountantToken = token(accountant);
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/processing-mode")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "TRADITIONAL"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/calculate")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/contract")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractNumber", "CTR-" + unique(), "contractDate", "2026-08-30",
                                "sentChannel", "DIDOX", "sentAt", "2026-08-30T10:00:00Z"))))
                .andExpect(status().isOk());

        BigDecimal contractAmount = jdbc.queryForObject(
                "SELECT contract_amount FROM payment WHERE case_id = ?", BigDecimal.class, caseId);
        return new PaymentFixture(caseId, contractAmount, accountant);
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

    @Test
    void twoSimultaneousConfirmPaymentsWithTheSameExternalReferenceNeverDoubleCountTheAmount() throws Exception {
        PaymentFixture fx = buildCaseReadyForPaymentConfirmation("C03");
        String externalReference = "EXT-" + unique();
        String body = objectMapper.writeValueAsString(Map.of(
                "amount", fx.contractAmount().toString(), "externalReference", externalReference));
        String accountantToken = token(fx.accountant());

        ExecutorService executor = twoThreads();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> confirmCall = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/accounting/cases/" + fx.caseId() + "/payment/confirm")
                            .header("Authorization", "Bearer " + accountantToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> r1 = executor.submit(confirmCall);
        Future<Integer> r2 = executor.submit(confirmCall);
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        int s1 = r1.get(10, TimeUnit.SECONDS);
        int s2 = r2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Neither call may 5xx; the DB state is the real assertion (WORKFLOW_ENGINE_DESIGN.md 12:
        // "409 or a rejected duplicate" for the loser - an idempotent-replay 200 is equally correct
        // if it happens to read after the winner already committed).
        assertThat(List.of(s1, s2)).allMatch(s -> s < 500);

        Integer confirmationRows = jdbc.queryForObject(
                "SELECT count(*) FROM payment_confirmation WHERE payment_id = "
                        + "(SELECT id FROM payment WHERE case_id = ?) AND external_reference = ?",
                Integer.class, fx.caseId(), externalReference);
        assertThat(confirmationRows).isEqualTo(1);
        BigDecimal confirmedAmount = jdbc.queryForObject(
                "SELECT confirmed_amount FROM payment WHERE case_id = ?", BigDecimal.class, fx.caseId());
        assertThat(confirmedAmount).isEqualByComparingTo(fx.contractAmount());
    }

    // =====================================================================================
    // C-06: two simultaneous approval decisions closing the round - the round closes exactly once
    // =====================================================================================

    private record ApprovalFixture(UUID caseId, UUID documentId, WorkflowStage signing, User departmentHead,
            User specialist) {
    }

    private ApprovalFixture buildCaseWithOpenParallelApprovalRound(String prefix) throws Exception {
        Department department = seedDepartment(prefix);
        uz.ithunter.crm.application.Service service = seedService();
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 30);
        WorkflowStage pc = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage endorsement = seedStage(workflow, "ENDORSE", StageType.ENDORSEMENT, 2,
                "DOCUMENT_UNDER_ENDORSEMENT", department.getId(), null, true);
        WorkflowStage signing = seedStage(workflow, "SIGN", StageType.SIGNING, 3,
                "DOCUMENT_ON_SIGNING", department.getId(), null, true);
        WorkflowStage completion = seedStage(workflow, "DONE", StageType.COMPLETION, 4,
                "RESULT_ISSUED", department.getId(), null, true);
        seedTransition(workflow, null, pc.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pc.getId(), endorsement.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, endorsement.getId(), signing.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, signing.getId(), completion.getId(), ConditionType.ALWAYS, null, 1);

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        var result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        UUID caseId = UUID.fromString(result.getResponse().getHeader("Location").replace("/api/cases/", ""));

        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + token(specialist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());

        String docJson = mockMvc.perform(post("/api/cases/" + caseId + "/documents")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("documentType", "CERTIFICATE", "title", "Test certificate"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(((Map<?, ?>) objectMapper.readValue(docJson, Map.class)).get("id").toString());

        mockMvc.perform(post("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentRef", "s3://demo/" + unique(), "contentHash", "d".repeat(64),
                                "fileName", "cert.pdf", "mimeType", "application/pdf", "sizeBytes", 1024))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/approval-rounds")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "PARALLEL", "participants", List.of(
                                Map.of("kind", "USER", "userId", departmentHead.getId(), "required", true, "sequenceNo", 1),
                                Map.of("kind", "USER", "userId", specialist.getId(), "required", true, "sequenceNo", 2))))))
                .andExpect(status().isCreated());

        return new ApprovalFixture(caseId, documentId, signing, departmentHead, specialist);
    }

    @Test
    void twoSimultaneousApprovalDecisionsClosingTheRoundCloseItExactlyOnce() throws Exception {
        ApprovalFixture fx = buildCaseWithOpenParallelApprovalRound("C06");
        UUID deptHeadTaskId = jdbc.queryForObject(
                "SELECT id FROM approval_task WHERE participant_user_id = ? AND status IN ('SENT', 'IN_REVIEW')",
                UUID.class, fx.departmentHead().getId());
        UUID specialistTaskId = jdbc.queryForObject(
                "SELECT id FROM approval_task WHERE participant_user_id = ? AND status IN ('SENT', 'IN_REVIEW')",
                UUID.class, fx.specialist().getId());

        ExecutorService executor = twoThreads();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> approve1 = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/approval-tasks/" + deptHeadTaskId + "/approve")
                            .header("Authorization", "Bearer " + token(fx.departmentHead()))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
        };
        Callable<Integer> approve2 = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/approval-tasks/" + specialistTaskId + "/approve")
                            .header("Authorization", "Bearer " + token(fx.specialist()))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> r1 = executor.submit(approve1);
        Future<Integer> r2 = executor.submit(approve2);
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        int s1 = r1.get(10, TimeUnit.SECONDS);
        int s2 = r2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(List.of(s1, s2)).allMatch(s -> s == 200);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM document_version WHERE document_id = ? AND version_no = 1",
                        String.class, fx.documentId()))
                .isEqualTo("ENDORSED");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?",
                        String.class, fx.caseId(), fx.signing().getId()))
                .isEqualTo("ACTIVE");
        Integer signingActivations = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE case_id = ? AND action = 'STAGE_ACTIVATED' "
                        + "AND entity_id = (SELECT id FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?)",
                Integer.class, fx.caseId(), fx.caseId(), fx.signing().getId());
        assertThat(signingActivations).isEqualTo(1);
    }
}
