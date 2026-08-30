package uz.ithunter.crm.casemodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.audit.AuditAction;
import uz.ithunter.crm.audit.AuditLog;
import uz.ithunter.crm.casemodule.engine.WorkflowEngine;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * Phase 7 DoD (FINAL_IMPLEMENTATION_ORDER.md): I-01, I-02, I-06 … I-09, W-01, W-03 pass;
 * registration is a single transaction; the tracking response is produced by
 * {@code ApplicantTrackingMapper} and S-07 passes on the raw JSON.
 */
class CaseLifecycleIntegrationTest extends AbstractCaseIntegrationTest {

    @Autowired
    private WorkflowEngine workflowEngine;

    /** A sequential three-stage route: entry PRIMARY_CHECK -> ACCOUNTING -> COMPLETION, all ALWAYS. */
    private record SequentialRoute(Workflow workflow, WorkflowStage primaryCheck, WorkflowStage accounting,
            WorkflowStage completion) {
    }

    private SequentialRoute seedSequentialRoute(UUID serviceId, UUID mainDepartmentId) {
        Workflow workflow = seedWorkflow(serviceId, mainDepartmentId, 30);
        WorkflowStage primaryCheck = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", mainDepartmentId, null, true);
        WorkflowStage accounting = seedStage(workflow, "ACC", StageType.ACCOUNTING, 2,
                "CONTRACT_SENT", mainDepartmentId, null, true);
        WorkflowStage completion = seedStage(workflow, "DONE", StageType.COMPLETION, 3,
                "RESULT_ISSUED", mainDepartmentId, null, true);
        seedTransition(workflow, null, primaryCheck.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, primaryCheck.getId(), accounting.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, accounting.getId(), completion.getId(), ConditionType.ALWAYS, null, 1);
        return new SequentialRoute(workflow, primaryCheck, accounting, completion);
    }

    private UUID registerCase(User staffUser, UUID applicationId) throws Exception {
        String staffToken = token(staffUser);
        MvcResult result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    // ---- I-01, I-02, and the "registration is a single transaction" DoD line ----

    @Test
    void registeringAnApplicationCreatesOneCaseWithOneStageRowPerWorkflowStageAndTheAuditTrio()
            throws Exception {
        Department department = seedDepartment("REG");
        uz.ithunter.crm.application.Service service = seedService();
        SequentialRoute route = seedSequentialRoute(service.getId(), department.getId());

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());

        UUID caseId = registerCase(departmentHead, applicationId);

        // I-01: exactly one ElectronicCase, a case number, one case_stage row per workflow stage (3).
        assertThat(electronicCaseRepository.existsByApplicationId(applicationId)).isTrue();
        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getCaseNumber()).isNotBlank();
        List<CaseStage> stages = caseStageRepository.findByCaseId(caseId);
        assertThat(stages).hasSize(3);

        // I-02: workflow_id is pinned to the ACTIVE version, and the entry stage is activated.
        assertThat(electronicCase.getWorkflowId()).isEqualTo(route.workflow().getId());
        assertThat(electronicCase.getCurrentStageId()).isEqualTo(route.primaryCheck().getId());
        CaseStage entryStage = caseStageRepository
                .findByCaseIdAndWorkflowStageId(caseId, route.primaryCheck().getId()).orElseThrow();
        assertThat(entryStage.getStatus()).isEqualTo(CaseStageStatus.ACTIVE);
        assertThat(entryStage.getActivationCount()).isEqualTo(1);

        // The audit trio: CASE_CREATED, CASE_REGISTERED, STAGE_ACTIVATED, all scoped to this case.
        List<AuditLog> auditRows = auditLogRepository.findByCaseId(caseId, Pageable.unpaged()).getContent();
        List<AuditAction> actions = auditRows.stream().map(AuditLog::getAction).toList();
        assertThat(actions).contains(AuditAction.CASE_CREATED, AuditAction.CASE_REGISTERED,
                AuditAction.STAGE_ACTIVATED);
        assertThat(auditRows).allSatisfy(row -> assertThat(row.getCaseId()).isEqualTo(caseId));
    }

    @Test
    void registeringATwiceSubmittedApplicationIsRejectedAsAlreadyRegistered() throws Exception {
        Department department = seedDepartment("DUP");
        uz.ithunter.crm.application.Service service = seedService();
        seedSequentialRoute(service.getId(), department.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());

        registerCase(departmentHead, applicationId);

        // The application is REGISTERED by now, so a second attempt fails the status check before it
        // ever reaches the ALREADY_REGISTERED guard - the two checks protect different failure modes,
        // and this is the one an identical retry through the real API actually hits.
        String staffToken = token(departmentHead);
        mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- W-01: sequential route advances stage by stage to COMPLETION ----

    @Test
    void sequentialRouteAdvancesStageByStageToCompletion() throws Exception {
        Department department = seedDepartment("SEQ");
        uz.ithunter.crm.application.Service service = seedService();
        SequentialRoute route = seedSequentialRoute(service.getId(), department.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        // Primary check ACCEPTED completes PC and the engine advances to ACC.
        String specialistToken = token(specialist);
        Map<String, Object> body = Map.of("category", "GREEN", "decision", "ACCEPTED");
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ElectronicCase afterPrimaryCheck = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(afterPrimaryCheck.getStatus()).isEqualTo(CaseStatus.IN_ACCOUNTING);
        assertThat(afterPrimaryCheck.getCurrentStageId()).isEqualTo(route.accounting().getId());
        assertThat(caseStageRepository.findByCaseIdAndWorkflowStageId(caseId, route.primaryCheck().getId())
                .orElseThrow().getStatus()).isEqualTo(CaseStageStatus.COMPLETED);

        // Phase 8 owns the endpoint that would complete ACCOUNTING; drive the engine directly here,
        // exactly as that phase's AccountingService will.
        workflowEngine.completeStage(caseId, route.accounting().getId());

        ElectronicCase completed = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(CaseStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(caseStageRepository.findByCaseIdAndWorkflowStageId(caseId, route.completion().getId())
                .orElseThrow().getStatus()).isEqualTo(CaseStageStatus.COMPLETED);
    }

    // ---- W-03: current_stage_id NULL while a parallel group is open; one external stage shown ----

    @Test
    void parallelGroupLeavesCurrentStageNullButTheApplicantSeesOneExternalStage() throws Exception {
        Department department = seedDepartment("PAR");
        uz.ithunter.crm.application.Service service = seedService();
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 30);
        WorkflowStage primaryCheck = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage accounting = seedStage(workflow, "ACC", StageType.ACCOUNTING, 2,
                "CONTRACT_SENT", department.getId(), null, true);
        WorkflowStage lab = seedStage(workflow, "LAB", StageType.EXECUTION, 3,
                "WORK_IN_PROGRESS", department.getId(), "PARALLEL_1", true);
        WorkflowStage expert = seedStage(workflow, "EXPERT", StageType.EXECUTION, 4,
                "WORK_IN_PROGRESS", department.getId(), "PARALLEL_1", true);
        WorkflowStage completion = seedStage(workflow, "DONE", StageType.COMPLETION, 5,
                "RESULT_ISSUED", department.getId(), null, true);
        seedTransition(workflow, null, primaryCheck.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, primaryCheck.getId(), accounting.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, accounting.getId(), lab.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, accounting.getId(), expert.getId(), ConditionType.ALWAYS, null, 2);
        seedTransition(workflow, lab.getId(), completion.getId(),
                ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "PARALLEL_1", 1);
        seedTransition(workflow, expert.getId(), completion.getId(),
                ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "PARALLEL_1", 1);

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        String specialistToken = token(specialist);
        Map<String, Object> body = Map.of("category", "GREEN", "decision", "ACCEPTED");
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Drive ACCOUNTING to completion directly (Phase 8's job in production) -> both LAB and EXPERT
        // activate together, since advance() does not stop at the first matching transition.
        workflowEngine.completeStage(caseId, accounting.getId());

        ElectronicCase withParallelOpen = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(withParallelOpen.getCurrentStageId()).isNull();
        List<CaseStage> active = caseStageRepository.findByCaseIdAndStatus(caseId, CaseStageStatus.ACTIVE);
        assertThat(active).hasSize(2);

        // The applicant still sees exactly ONE external stage (the mapped WORK_IN_PROGRESS), never two.
        String applicantToken = token(applicantUser);
        String trackingJson = mockMvc.perform(get("/api/cases/" + caseId + "/tracking")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> tracking = objectMapper.readValue(trackingJson, Map.class);
        Map<?, ?> externalStage = (Map<?, ?>) tracking.get("externalStage");
        assertThat(externalStage).isNotNull();
        assertThat(externalStage.get("code")).isEqualTo("WORK_IN_PROGRESS");

        // Completing only one of the two required siblings must not open COMPLETION yet.
        workflowEngine.completeStage(caseId, lab.getId());
        assertThat(caseStageRepository.findByCaseIdAndWorkflowStageId(caseId, completion.getId())
                .orElseThrow().getStatus()).isEqualTo(CaseStageStatus.PENDING);

        // Completing the second one opens the gate.
        workflowEngine.completeStage(caseId, expert.getId());
        ElectronicCase finished = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(CaseStatus.COMPLETED);
    }

    // ---- I-06, I-09: category and decision persist independently; a missing reason is 400 ----

    @Test
    void primaryCheckWritesCategoryAndDecisionSeparatelyAndBothPersist() throws Exception {
        Department department = seedDepartment("PC1");
        uz.ithunter.crm.application.Service service = seedService();
        seedSequentialRoute(service.getId(), department.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        String specialistToken = token(specialist);
        Map<String, Object> body = Map.of("category", "YELLOW", "decision", "ACCEPTED");
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getPrimaryCheckCategory()).isEqualTo(PrimaryCheckCategory.YELLOW);
        assertThat(electronicCase.getPrimaryCheckDecision()).isEqualTo(PrimaryCheckDecision.ACCEPTED);
        PrimaryCheck check = primaryCheckRepository.findFirstByCaseIdOrderByAttemptNoDesc(caseId)
                .orElseThrow();
        assertThat(check.getCategory()).isEqualTo(PrimaryCheckCategory.YELLOW);
        assertThat(check.getDecision()).isEqualTo(PrimaryCheckDecision.ACCEPTED);
    }

    @Test
    void primaryCheckDecisionWithoutAReasonIsRejected() throws Exception {
        Department department = seedDepartment("PC2");
        uz.ithunter.crm.application.Service service = seedService();
        seedSequentialRoute(service.getId(), department.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        String specialistToken = token(specialist);
        Map<String, Object> body = Map.of("category", "RED", "decision", "REJECTED");
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ---- I-07: RETURNED_TO_APPLICANT moves the case to RETURNED and tracking shows reason+remarks ----

    @Test
    void returnedToApplicantMovesCaseToReturnedAndTrackingShowsReasonAndRemarks() throws Exception {
        Department department = seedDepartment("RET");
        uz.ithunter.crm.application.Service service = seedService();
        seedSequentialRoute(service.getId(), department.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        String specialistToken = token(specialist);
        Map<String, Object> body = Map.of(
                "category", "RED",
                "decision", "RETURNED_TO_APPLICANT",
                "reason", "Missing notarized copy of the passport",
                "checklist", Map.of("remarks", "Please attach a notarized copy"));
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getStatus()).isEqualTo(CaseStatus.RETURNED);

        String applicantToken = token(applicantUser);
        String trackingJson = mockMvc.perform(get("/api/cases/" + caseId + "/tracking")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> tracking = objectMapper.readValue(trackingJson, Map.class);
        Map<?, ?> returnedForCorrection = (Map<?, ?>) tracking.get("returnedForCorrection");
        assertThat(returnedForCorrection).isNotNull();
        assertThat(returnedForCorrection.get("reason")).isEqualTo("Missing notarized copy of the passport");
        assertThat(returnedForCorrection.get("remarks")).isEqualTo("Please attach a notarized copy");
    }

    // ---- I-08: ROUTE_CHANGED rebinds the case to the named workflow and audits it ----

    @Test
    void routeChangedRebindsTheCaseToTheNamedWorkflowAndAudits() throws Exception {
        Department department = seedDepartment("RC1");
        uz.ithunter.crm.application.Service service = seedService();
        seedSequentialRoute(service.getId(), department.getId());

        // A second, independent ACTIVE route on its OWN service - findByServiceIdAndStatus... orders
        // ACTIVE workflows by code, so a second ACTIVE route on the SAME service would make which one
        // register() picks non-deterministic.
        uz.ithunter.crm.application.Service altService = seedService();
        Workflow altWorkflow = seedWorkflow(altService.getId(), department.getId(), 10);
        WorkflowStage altEntry = seedStage(altWorkflow, "ALT_PC", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        seedTransition(altWorkflow, null, altEntry.getId(), ConditionType.ALWAYS, null, 1);

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        String specialistToken = token(specialist);
        Map<String, Object> body = Map.of(
                "category", "YELLOW",
                "decision", "ROUTE_CHANGED",
                "reason", "Wrong route selected at intake",
                "newWorkflowId", altWorkflow.getId().toString());
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ElectronicCase electronicCase = electronicCaseRepository.findById(caseId).orElseThrow();
        assertThat(electronicCase.getWorkflowId()).isEqualTo(altWorkflow.getId());
        assertThat(electronicCase.getCurrentStageId()).isEqualTo(altEntry.getId());

        // The abandoned route's PC stage was already ACTIVE, so it is preserved as CANCELLED (history),
        // not discarded - only never-reached PENDING rows of the old route are deleted.
        CaseStage newEntryStage = caseStageRepository
                .findByCaseIdAndWorkflowStageId(caseId, altEntry.getId()).orElseThrow();
        assertThat(newEntryStage.getStatus()).isEqualTo(CaseStageStatus.ACTIVE);
        List<CaseStage> stages = caseStageRepository.findByCaseId(caseId);
        assertThat(stages).hasSize(2);
        assertThat(stages).filteredOn(s -> !s.getWorkflowStageId().equals(altEntry.getId()))
                .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(CaseStageStatus.CANCELLED));

        List<AuditAction> actions = auditLogRepository.findByCaseId(caseId, Pageable.unpaged())
                .getContent().stream().map(AuditLog::getAction).toList();
        assertThat(actions).contains(AuditAction.ROUTE_CHANGED);
    }

    // ---- S-05: an applicant reading another applicant's case gets 404, not 403 ----

    @Test
    void applicantReadingAnotherApplicantsCaseGets404NotForbidden() throws Exception {
        Department department = seedDepartment("S05");
        uz.ithunter.crm.application.Service service = seedService();
        seedSequentialRoute(service.getId(), department.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        // A second, unrelated applicant - CASE:VIEW is held by APPLICANT, so this reaches the
        // object-level check, not the @PreAuthorize layer.
        Applicant otherApplicant = seedApplicant();
        User otherApplicantUser = seedApplicantUser(otherApplicant);
        String otherToken = token(otherApplicantUser);

        mockMvc.perform(get("/api/cases/" + caseId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    // ---- S-07: the applicant tracking payload leaks nothing internal, on the raw JSON ----

    @Test
    void applicantTrackingJsonContainsNoInternalFields() throws Exception {
        Department department = seedDepartment("S07");
        uz.ithunter.crm.application.Service service = seedService();
        seedSequentialRoute(service.getId(), department.getId());
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        UUID caseId = registerCase(departmentHead, applicationId);

        String applicantToken = token(applicantUser);
        String trackingJson = mockMvc.perform(get("/api/cases/" + caseId + "/tracking")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Field names that belong only to the internal CaseResponse / entities must never appear.
        assertThat(trackingJson).doesNotContain("currentStage")
                .doesNotContain("activeStages")
                .doesNotContain("mainResponsibleDepartment")
                .doesNotContain("participatingDepartments")
                .doesNotContain("internalStatusLabel")
                .doesNotContain("primaryCheckCategory")
                .doesNotContain("primaryCheckDecision")
                .doesNotContain("stageType")
                .doesNotContain("finance")
                .doesNotContain("performedWork")
                // internal stage/department identifiers must not leak either.
                .doesNotContain(department.getId().toString())
                .doesNotContain("PC stage")
                .doesNotContain("ACC stage");
    }
}
