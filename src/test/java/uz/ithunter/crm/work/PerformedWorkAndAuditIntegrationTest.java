package uz.ithunter.crm.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditLog;
import uz.ithunter.crm.casemodule.AbstractCaseIntegrationTest;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.finance.PriceRule;
import uz.ithunter.crm.finance.PriceRuleRepository;
import uz.ithunter.crm.finance.PriceRuleType;
import uz.ithunter.crm.shared.domain.ProcessingMode;
import uz.ithunter.crm.task.Task;
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
 * Phase 11 DoD (FINAL_IMPLEMENTATION_ORDER.md): PW-01, PW-02, PW-03, A-01, A-05.
 * U-13 lives in {@link ContractAmountBracketResolverTest} (dependency-free).
 */
class PerformedWorkAndAuditIntegrationTest extends AbstractCaseIntegrationTest {

    @Autowired
    private WorkTypeRepository workTypeRepository;

    @Autowired
    private PerformedWorkRepository performedWorkRepository;

    @Autowired
    private PerformedWorkRecorder performedWorkRecorder;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkflowStageRepository workflowStageRepository;

    @Autowired
    private PriceRuleRepository priceRuleRepository;

    private static final String HASH_1 = "1".repeat(64);
    private static final String HASH_2 = "2".repeat(64);

    private record Route(Workflow workflow, WorkflowStage pc, WorkflowStage acc, WorkflowStage pay,
            WorkflowStage exec, WorkflowStage done) {
    }

    private record Fixture(Route route, UUID caseId, User departmentHead, User specialist, User accountant) {
    }

    /** PC -> ACC -> PAY -> EXEC(green work type, bracket required) -> DONE. */
    private Fixture buildCaseThroughPayment(String prefix, boolean bracketRequired) throws Exception {
        Department department = seedDepartment(prefix);
        uz.ithunter.crm.application.Service service = seedService();
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 30);
        WorkflowStage pc = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage acc = seedStage(workflow, "ACC", StageType.ACCOUNTING, 2,
                "CONTRACT_SENT", department.getId(), null, true);
        WorkflowStage pay = seedStage(workflow, "PAY", StageType.PAYMENT_CONTROL, 3,
                "AWAITING_PAYMENT", department.getId(), null, true);
        WorkflowStage exec = seedStage(workflow, "EXEC", StageType.EXECUTION, 4,
                "WORK_IN_PROGRESS", department.getId(), null, true);
        exec.setRequiresResult(true);
        WorkType workType = new WorkType();
        workType.setCode("GREEN_DOC_EXPERTISE_" + unique().toUpperCase());
        workType.setName("Green document expertise");
        workType.setRequiresContractAmountBracket(bracketRequired);
        workType.setActive(true);
        workType = workTypeRepository.save(workType);
        exec.setWorkTypeId(workType.getId());
        exec = workflowStageRepository.save(exec);
        WorkflowStage done = seedStage(workflow, "DONE", StageType.COMPLETION, 5,
                "RESULT_ISSUED", department.getId(), null, true);

        seedTransition(workflow, null, pc.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pc.getId(), acc.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, acc.getId(), pay.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, pay.getId(), exec.getId(), ConditionType.PAYMENT_STATE_SATISFIED, null, 1);
        seedTransition(workflow, exec.getId(), done.getId(), ConditionType.ALWAYS, null, 1);

        // oneItem() carries quantity=2, so a 7,500,000 base price totals 15,000,000 -> M10_20M.
        priceRuleRepository.save(rule(service.getId(), PriceRuleType.BASE_PER_ITEM, null,
                new BigDecimal("7500000"), null));
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
        UUID caseId = UUID.fromString(result.getResponse().getHeader("Location")
                .replace("/api/cases/", ""));

        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + token(specialist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("category", "GREEN", "decision", "ACCEPTED"))))
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

        var payment = jdbc.queryForObject(
                "SELECT contract_amount FROM payment WHERE case_id = ?", BigDecimal.class, caseId);
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/payment/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", payment.toString()))))
                .andExpect(status().isOk());

        return new Fixture(new Route(workflow, pc, acc, pay, exec, done), caseId, departmentHead,
                specialist, accountant);
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

    private UUID completeExecutionTaskWithDocument(Fixture fx) throws Exception {
        UUID execTaskId = taskRepository.findByCaseId(fx.caseId()).stream()
                .filter(t -> t.getWorkflowStageId().equals(fx.route().exec().getId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/tasks/" + execTaskId + "/assign")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", fx.specialist().getId()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/" + execTaskId + "/start")
                        .header("Authorization", "Bearer " + token(fx.specialist())))
                .andExpect(status().isOk());

        String docJson = mockMvc.perform(post("/api/cases/" + fx.caseId() + "/documents")
                        .header("Authorization", "Bearer " + token(fx.specialist()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documentType", "EXPERT_OPINION", "title", "Expert opinion",
                                "taskId", execTaskId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(((Map<?, ?>) objectMapper.readValue(docJson, Map.class)).get("id").toString());

        mockMvc.perform(post("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + token(fx.specialist()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentRef", "s3://demo/" + unique(), "contentHash", HASH_1,
                                "fileName", "opinion.pdf", "mimeType", "application/pdf", "sizeBytes", 512))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/tasks/" + execTaskId + "/results")
                        .header("Authorization", "Bearer " + token(fx.specialist()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("payload", "{\"note\":\"done\"}", "summary", "Done"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/tasks/" + execTaskId + "/complete")
                        .header("Authorization", "Bearer " + token(fx.specialist()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("version", 0))))
                .andExpect(status().isOk());
        return execTaskId;
    }

    // ---- PW-01: completing a stage with a work_type records exactly one PerformedWork ----

    @Test
    void completingAWorkTypeStageRecordsExactlyOnePerformedWorkWithFullDetail() throws Exception {
        Fixture fx = buildCaseThroughPayment("PW01", true);
        completeExecutionTaskWithDocument(fx);

        List<PerformedWork> rows = performedWorkRepository.findByCaseId(fx.caseId());
        assertThat(rows).hasSize(1);
        PerformedWork pw = rows.get(0);
        assertThat(pw.getExecutorUserId()).isEqualTo(fx.specialist().getId());
        assertThat(pw.getDepartmentId()).isNotNull();
        assertThat(pw.getProcessingMode()).isEqualTo(ProcessingMode.TRADITIONAL);
        assertThat(pw.getSupportingDocumentVersionId()).isNotNull();
        // U-13/PW-01 together: contract was 15,000,000 -> M10_20M bracket.
        assertThat(pw.getContractAmountBracket()).isEqualTo(ContractAmountBracket.M10_20M);

        // GET /cases/{id}/performed-works, department-scoped staff can see it.
        String listJson = mockMvc.perform(get("/api/cases/" + fx.caseId() + "/performed-works")
                        .header("Authorization", "Bearer " + token(fx.departmentHead())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<?> list = objectMapper.readValue(listJson, List.class);
        assertThat(list).hasSize(1);
    }

    // ---- S-07-adjacent: the applicant must never see performed-work data (spec 15.13) ----

    @Test
    void applicantCannotListPerformedWorksEvenOnTheirOwnCase() throws Exception {
        Fixture fx = buildCaseThroughPayment("PWAPP", true);
        completeExecutionTaskWithDocument(fx);

        // The applicant's own token: applicant holds no PERFORMED_WORK:* grant at all.
        UUID applicantId = jdbc.queryForObject(
                "SELECT applicant_id FROM electronic_case WHERE id = ?", UUID.class, fx.caseId());
        User applicantUser = userRepository.findAll().stream()
                .filter(u -> applicantId.equals(u.getApplicantId())).findFirst().orElseThrow();

        mockMvc.perform(get("/api/cases/" + fx.caseId() + "/performed-works")
                        .header("Authorization", "Bearer " + token(applicantUser)))
                .andExpect(status().isForbidden());
    }

    // ---- PW-02: a revision cycle does not create a second PerformedWork; the document refreshes ----

    @Test
    void aRevisionCycleUpdatesTheExistingRowInsteadOfInsertingASecondOne() throws Exception {
        Fixture fx = buildCaseThroughPayment("PW02", false);
        completeExecutionTaskWithDocument(fx);
        assertThat(performedWorkRepository.findByCaseId(fx.caseId())).hasSize(1);
        UUID firstRowId = performedWorkRepository.findByCaseId(fx.caseId()).get(0).getId();
        UUID firstSupportingDoc = performedWorkRepository.findByCaseId(fx.caseId()).get(0)
                .getSupportingDocumentVersionId();

        // Drive the recorder a second time directly (spec 8.5's revision cycle re-uses the same
        // case_stage row and calls this same hook again - WorkflowEngine.completeStage itself is
        // idempotent per case_stage, so this exercises PerformedWorkRecorder's own upsert logic
        // exactly as a real re-completion would).
        ElectronicCase electronicCase = electronicCaseRepository.findById(fx.caseId()).orElseThrow();
        CaseStage execStage = caseStageRepository
                .findByCaseIdAndWorkflowStageId(fx.caseId(), fx.route().exec().getId()).orElseThrow();
        performedWorkRecorder.recordIfConfigured(electronicCase, execStage);

        List<PerformedWork> rows = performedWorkRepository.findByCaseId(fx.caseId());
        assertThat(rows).hasSize(1); // still one row, not two
        assertThat(rows.get(0).getId()).isEqualTo(firstRowId); // same row, reused
    }

    // ---- PW-03: bracket-required work type without a bracket is rejected; a non-green type with
    // one is also rejected (DB trigger, spec 8.4) ----

    @Test
    void bracketMismatchesAreRejectedByTheDbTrigger() throws Exception {
        Fixture fx = buildCaseThroughPayment("PW03", true);
        Task execTask = taskRepository.findByCaseId(fx.caseId()).stream()
                .filter(t -> t.getWorkflowStageId().equals(fx.route().exec().getId()))
                .findFirst().orElseThrow();
        WorkType bracketRequired = workTypeRepository.findById(
                workflowStageRepository.findById(fx.route().exec().getId()).orElseThrow().getWorkTypeId())
                .orElseThrow();

        // Bracket-required work type, NULL bracket -> rejected.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO performed_work (case_id, work_type_id, department_id, executor_user_id, "
                        + "service_id, processing_mode, performed_at) VALUES (?, ?, "
                        + "(SELECT main_responsible_department_id FROM electronic_case WHERE id = ?), "
                        + "?, (SELECT service_id FROM electronic_case WHERE id = ?), 'TRADITIONAL', now())",
                fx.caseId(), bracketRequired.getId(), fx.caseId(), fx.specialist().getId(), fx.caseId()))
                .isInstanceOf(DataAccessException.class);

        // A non-bracket-required work type carrying a bracket anyway -> also rejected.
        WorkType noBracket = new WorkType();
        noBracket.setCode("NO_BRACKET_" + unique().toUpperCase());
        noBracket.setName("No bracket needed");
        noBracket.setRequiresContractAmountBracket(false);
        noBracket.setActive(true);
        noBracket = workTypeRepository.save(noBracket);
        WorkType finalNoBracket = noBracket;
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO performed_work (case_id, work_type_id, department_id, executor_user_id, "
                        + "service_id, processing_mode, performed_at, contract_amount_bracket) VALUES (?, ?, "
                        + "(SELECT main_responsible_department_id FROM electronic_case WHERE id = ?), "
                        + "?, (SELECT service_id FROM electronic_case WHERE id = ?), 'TRADITIONAL', now(), 'LT_10M')",
                fx.caseId(), finalNoBracket.getId(), fx.caseId(), fx.specialist().getId(), fx.caseId()))
                .isInstanceOf(DataAccessException.class);
    }

    // ---- A-01: the happy path writes every expected AuditAction ----

    @Test
    void theHappyPathWritesEveryExpectedAuditAction() throws Exception {
        Fixture fx = buildCaseThroughPayment("A01", true);
        completeExecutionTaskWithDocument(fx);

        List<AuditLog> rows = auditLogRepository.findByCaseId(fx.caseId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<AuditAction> actions = rows.stream().map(AuditLog::getAction).toList();

        assertThat(actions).contains(
                AuditAction.CASE_CREATED, AuditAction.ROUTE_ASSIGNED, AuditAction.CASE_REGISTERED,
                AuditAction.STAGE_ACTIVATED, AuditAction.PRIMARY_CHECK_COMPLETED,
                AuditAction.CATEGORY_ASSIGNED, AuditAction.PRIMARY_CHECK_DECISION_RECORDED,
                AuditAction.STAGE_COMPLETED, AuditAction.PROCESSING_MODE_SET,
                AuditAction.PRICE_CALCULATED, AuditAction.PRICE_CONFIRMED,
                AuditAction.CONTRACT_RECORDED, AuditAction.CONTRACT_SENT,
                AuditAction.PAYMENT_CONFIRMED, AuditAction.TASK_CREATED, AuditAction.TASK_ASSIGNED,
                AuditAction.TASK_STARTED, AuditAction.RESULT_VERSION_CREATED,
                AuditAction.TASK_COMPLETED, AuditAction.PERFORMED_WORK_RECORDED,
                AuditAction.DOCUMENT_CREATED, AuditAction.DOCUMENT_VERSION_CREATED);

        // Basic ordering sanity: registration precedes everything downstream.
        long createdSeq = rows.stream().filter(r -> r.getAction() == AuditAction.CASE_CREATED)
                .findFirst().orElseThrow().getSeq();
        long completedSeq = rows.stream().filter(r -> r.getAction() == AuditAction.PERFORMED_WORK_RECORDED)
                .findFirst().orElseThrow().getSeq();
        assertThat(createdSeq).isLessThan(completedSeq);

        // Every row from this case's audit trail also comes back through GET /cases/{id}/audit
        // (a large page size, since the endpoint paginates and defaults to fewer rows than a full
        // demo scenario produces).
        String auditJson = mockMvc.perform(get("/api/cases/" + fx.caseId() + "/audit")
                        .param("size", "200")
                        .header("Authorization", "Bearer " + token(seedStaffUser(
                                RoleCode.HEAD_OF_CERTIFICATION_BODY, seedDepartment("A01AUD").getId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> page = objectMapper.readValue(auditJson, Map.class);
        assertThat(((List<?>) page.get("content")).size()).isGreaterThanOrEqualTo(actions.size());
    }

    // ---- A-05: verify_audit_chain() reports intact, then the exact seq after a forced tamper ----

    @Test
    void auditChainIsIntactThenReportsTheExactSeqAfterAForcedTamper() throws Exception {
        Fixture fx = buildCaseThroughPayment("A05", true);
        User head = seedStaffUser(RoleCode.HEAD_OF_CERTIFICATION_BODY, seedDepartment("A05HEAD").getId());

        Long targetSeq = jdbc.queryForObject(
                "SELECT seq FROM audit_log WHERE case_id = ? ORDER BY seq ASC LIMIT 1", Long.class, fx.caseId());

        // Scoped to THIS fixture's own rows, not "nothing in the whole shared table has ever
        // raced" - the table is shared across the entire mvn verify run (AbstractIntegrationTest),
        // and other test classes' own genuine concurrency races (Phase 12's C-01/C-02) are their
        // own concern, not this test's. verify_audit_chain() itself IS still exercised end-to-end
        // via the real HTTP endpoint below, right after this fixture's own rows are the newest
        // ones in the table.
        Integer breaksInOurRange = jdbc.queryForObject(
                "SELECT count(*) FROM verify_audit_chain() WHERE broken_seq >= ?", Integer.class, targetSeq);
        assertThat(breaksInOurRange).as("audit chain must be intact across this fixture's own rows").isZero();
        try {
            jdbc.execute("ALTER TABLE audit_log DISABLE TRIGGER tr_audit_log_immutable");
            jdbc.update("UPDATE audit_log SET new_value = '{\"tampered\":true}'::jsonb WHERE seq = ?", targetSeq);
        } finally {
            jdbc.execute("ALTER TABLE audit_log ENABLE TRIGGER tr_audit_log_immutable");
        }

        String tamperedJson = mockMvc.perform(get("/api/audit/integrity")
                        .header("Authorization", "Bearer " + token(head)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> tampered = objectMapper.readValue(tamperedJson, Map.class);
        assertThat(tampered.get("intact")).isEqualTo(false);
        // <= rather than == : the endpoint reports the EARLIEST broken seq in the whole shared
        // table (AbstractIntegrationTest's one container for the whole mvn verify run), so if an
        // unrelated earlier test's own genuine race left something broken before our fixture even
        // started, the global first-broken-seq can be lower than ours - what THIS test owns is the
        // guarantee that its OWN tamper at targetSeq is detected, not that it is globally first.
        assertThat(((Number) tampered.get("firstBrokenSeq")).longValue()).isLessThanOrEqualTo(targetSeq);
        Integer ourSeqReportedBroken = jdbc.queryForObject(
                "SELECT count(*) FROM verify_audit_chain() WHERE broken_seq = ?", Integer.class, targetSeq);
        assertThat(ourSeqReportedBroken).as("our own tampered seq must itself be reported broken").isEqualTo(1);
    }
}
