package uz.ithunter.crm.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.casemodule.AbstractCaseIntegrationTest;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * Phase 9 DoD (FINAL_IMPLEMENTATION_ORDER.md): W-02, W-04 … W-10, S-08, S-09.
 * {@link TaskExecutionIntegrationTest} already covers S-08, S-09 and W-04 end to end (through the
 * real HTTP endpoints, though its parallel task rows are seeded by hand rather than through stage
 * activation). This class covers the rows that had NO coverage at all - W-02 (through the REAL
 * activation pipeline: {@code WorkflowEngine} -> {@code ExecutionStageActivator} ->
 * {@code TaskFactory}, not a hand-inserted row), W-05, W-06, W-07, W-09, W-10.
 */
class TaskLifecycleIntegrationTest extends AbstractCaseIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    private record Route(Workflow workflow, WorkflowStage split, WorkflowStage p1, WorkflowStage p2,
            WorkflowStage p3Optional, WorkflowStage join, WorkflowStage done) {
    }

    /**
     * SPLIT (PRIMARY_CHECK, entry) -&gt; P1 &amp; P2 (EXECUTION, required) &amp; P3 (EXECUTION,
     * OPTIONAL) in parallel group "G" -&gt; JOIN (FINAL_REVIEW, gated on the group) -&gt; DONE.
     * P1 additionally requires an approved result before it can complete.
     */
    private Route seedParallelRoute(uz.ithunter.crm.application.Service service, Department department) {
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 30);
        WorkflowStage split = seedStage(workflow, "SPLIT", StageType.PRIMARY_CHECK, 1,
                "DOCUMENTS_UNDER_REVIEW", department.getId(), null, true);
        WorkflowStage p1 = seedStage(workflow, "P1", StageType.EXECUTION, 2,
                "WORK_IN_PROGRESS", department.getId(), "G", true);
        p1.setRequiresResult(true);
        p1.setApprovalRequired(true);
        p1.setApprovalMode(uz.ithunter.crm.workflow.ApprovalMode.SEQUENTIAL);
        p1 = workflowStageRepository.save(p1);
        WorkflowStage p2 = seedStage(workflow, "P2", StageType.EXECUTION, 3,
                "WORK_IN_PROGRESS", department.getId(), "G", true);
        p2.setRequiresResult(false);
        p2 = workflowStageRepository.save(p2);
        WorkflowStage p3Optional = seedStage(workflow, "P3", StageType.EXECUTION, 4,
                "WORK_IN_PROGRESS", department.getId(), "G", false);
        p3Optional.setRequiresResult(false);
        p3Optional = workflowStageRepository.save(p3Optional);
        WorkflowStage join = seedStage(workflow, "JOIN", StageType.FINAL_REVIEW, 5,
                "FINAL_DOCUMENT_PREPARING", department.getId(), null, true);
        WorkflowStage done = seedStage(workflow, "DONE", StageType.COMPLETION, 6,
                "RESULT_ISSUED", department.getId(), null, true);

        seedTransition(workflow, null, split.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, split.getId(), p1.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, split.getId(), p2.getId(), ConditionType.ALWAYS, null, 2);
        seedTransition(workflow, split.getId(), p3Optional.getId(), ConditionType.ALWAYS, null, 3);
        seedTransition(workflow, p1.getId(), join.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "G", 1);
        seedTransition(workflow, p2.getId(), join.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "G", 2);
        seedTransition(workflow, p3Optional.getId(), join.getId(),
                ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "G", 3);
        seedTransition(workflow, join.getId(), done.getId(), ConditionType.ALWAYS, null, 1);
        return new Route(workflow, split, p1, p2, p3Optional, join, done);
    }

    private record Fixture(Route route, UUID caseId, User departmentHead, User specialist) {
    }

    private Fixture registerAndAcceptPrimaryCheck(String prefix) throws Exception {
        Department department = seedDepartment(prefix);
        uz.ithunter.crm.application.Service service = seedService();
        Route route = seedParallelRoute(service, department);
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        String staffToken = token(departmentHead);
        var result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID caseId = UUID.fromString(result.getResponse().getHeader("Location")
                .replace("/api/cases/", ""));

        String specialistToken = token(specialist);
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());
        return new Fixture(route, caseId, departmentHead, specialist);
    }

    private UUID taskFor(UUID caseId, UUID workflowStageId) {
        return taskRepository.findByCaseId(caseId).stream()
                .filter(t -> t.getWorkflowStageId().equals(workflowStageId))
                .findFirst().orElseThrow().getId();
    }

    private void assignStartAndMaybeSubmit(UUID taskId, User departmentHead, User assignee,
            boolean requiresResult) throws Exception {
        String deptHeadToken = token(departmentHead);
        mockMvc.perform(post("/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", assignee.getId()))))
                .andExpect(status().isOk());
        String assigneeToken = token(assignee);
        mockMvc.perform(post("/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + assigneeToken))
                .andExpect(status().isOk());
        if (requiresResult) {
            mockMvc.perform(post("/tasks/" + taskId + "/results")
                            .header("Authorization", "Bearer " + assigneeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("payload", "{\"note\":\"v1\"}", "summary", "First pass"))))
                    .andExpect(status().isCreated());
        }
    }

    private void completeTask(UUID taskId, User assignee) throws Exception {
        mockMvc.perform(post("/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token(assignee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("version", 0))))
                .andExpect(status().isOk());
    }

    // ---- W-02: completing the entry stage activates ALL parallel siblings via the REAL pipeline ----

    @Test
    void completingSplitActivatesAllThreeParallelStagesWithRealTasks() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("W02");

        List<Task> tasks = taskRepository.findByCaseId(fx.caseId());
        // Exactly P1, P2, P3 - created by ExecutionStageActivator/TaskFactory through real stage
        // activation, not seeded by the test. SPLIT (PRIMARY_CHECK) creates no task (A37).
        assertThat(tasks).hasSize(3);
        assertThat(tasks).extracting(Task::getWorkflowStageId)
                .containsExactlyInAnyOrder(fx.route().p1().getId(), fx.route().p2().getId(),
                        fx.route().p3Optional().getId());
        assertThat(tasks).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.CREATED));
    }

    // ---- W-05: one incomplete REQUIRED parallel task blocks the dependent stage ----

    @Test
    void oneIncompleteRequiredSiblingBlocksTheJoinStage() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("W05");
        UUID p1Task = taskFor(fx.caseId(), fx.route().p1().getId());
        assignStartAndMaybeSubmit(p1Task, fx.departmentHead(), fx.specialist(), true);
        mockMvc.perform(post("/tasks/" + p1Task + "/approve-result")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        completeTask(p1Task, fx.specialist()); // P1 done, P2 (required) still open

        String joinStatus = caseStageStatus(fx.caseId(), fx.route().join().getId());
        assertThat(joinStatus).isEqualTo("PENDING");
    }

    // ---- W-06: an incomplete OPTIONAL parallel task does NOT block the dependent stage ----
    // ---- W-07: completing the last required sibling activates FINAL_REVIEW exactly once ----

    @Test
    void optionalSiblingNeverBlocksAndTheJoinStageActivatesExactlyOnce() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("W06");
        UUID p1Task = taskFor(fx.caseId(), fx.route().p1().getId());
        UUID p2Task = taskFor(fx.caseId(), fx.route().p2().getId());
        // P3 (optional) is deliberately left untouched - CREATED forever.

        assignStartAndMaybeSubmit(p1Task, fx.departmentHead(), fx.specialist(), true);
        mockMvc.perform(post("/tasks/" + p1Task + "/approve-result")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        completeTask(p1Task, fx.specialist());

        assignStartAndMaybeSubmit(p2Task, fx.departmentHead(), fx.specialist(), false);
        completeTask(p2Task, fx.specialist()); // last REQUIRED sibling - P3 optional still open

        CaseStage joinStage = caseStageRepository.findByCaseIdAndWorkflowStageId(fx.caseId(),
                fx.route().join().getId()).orElseThrow();
        assertThat(joinStage.getStatus()).isEqualTo(CaseStageStatus.ACTIVE);
        assertThat(joinStage.getActivationCount()).isEqualTo(1); // W-07: exactly once

        // FinalReviewStageActivator created exactly one task for it too.
        List<Task> joinTasks = taskRepository.findByCaseId(fx.caseId()).stream()
                .filter(t -> t.getWorkflowStageId().equals(fx.route().join().getId()))
                .toList();
        assertThat(joinTasks).hasSize(1);
    }

    // ---- W-09: a new result version supersedes the old one; the old payload stays readable ----

    @Test
    void aNewResultVersionSupersedesTheOldOneAndTheOldPayloadStaysReadable() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("W09");
        UUID p1Task = taskFor(fx.caseId(), fx.route().p1().getId());
        assignStartAndMaybeSubmit(p1Task, fx.departmentHead(), fx.specialist(), true); // v1

        mockMvc.perform(post("/tasks/" + p1Task + "/results")
                        .header("Authorization", "Bearer " + token(fx.specialist()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payload", "{\"note\":\"v2\"}", "summary", "Corrected",
                                "revisionReason", "Fixed a typo in the first submission"))))
                .andExpect(status().isCreated());

        String resultsJson = mockMvc.perform(get("/tasks/" + p1Task + "/results")
                        .header("Authorization", "Bearer " + token(fx.specialist())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> results = objectMapper.readValue(resultsJson, List.class);
        assertThat(results).hasSize(2);
        Map<String, Object> v1 = results.stream().filter(r -> ((Number) r.get("versionNo")).intValue() == 1)
                .findFirst().orElseThrow();
        Map<String, Object> v2 = results.stream().filter(r -> ((Number) r.get("versionNo")).intValue() == 2)
                .findFirst().orElseThrow();
        assertThat(v1.get("status")).isEqualTo("SUPERSEDED");
        assertThat(v1.get("payload")).asString().contains("v1"); // old payload still readable
        assertThat(v2.get("status")).isEqualTo("SUBMITTED");
        assertThat(v2.get("supersedesId")).isEqualTo(v1.get("id"));
    }

    // ---- W-10: overwriting an APPROVED result -> 409; the DB trigger rejects it too ----

    @Test
    void overwritingAnApprovedResultIsRejectedByTheApiAndTheDbTrigger() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("W10");
        UUID p1Task = taskFor(fx.caseId(), fx.route().p1().getId());
        assignStartAndMaybeSubmit(p1Task, fx.departmentHead(), fx.specialist(), true);
        mockMvc.perform(post("/tasks/" + p1Task + "/approve-result")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Looks good"))))
                .andExpect(status().isOk());

        // API: submitting a new result on top of an APPROVED one is rejected.
        mockMvc.perform(post("/tasks/" + p1Task + "/results")
                        .header("Authorization", "Bearer " + token(fx.specialist()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payload", "{\"note\":\"v2\"}", "summary", "Attempted overwrite",
                                "revisionReason", "should be rejected"))))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("RESULT_ALREADY_APPROVED"));

        // DB: tr_task_result_guard rejects a direct UPDATE of an approved result's content, too.
        UUID approvedResultId = (UUID) jdbc.queryForObject(
                "SELECT id FROM task_result WHERE task_id = ? AND status = 'APPROVED'",
                UUID.class, p1Task);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE task_result SET payload = '{\"tampered\":true}'::jsonb WHERE id = ?",
                approvedResultId))
                .isInstanceOf(DataAccessException.class);
    }

    private String caseStageStatus(UUID caseId, UUID workflowStageId) {
        return caseStageRepository.findByCaseIdAndWorkflowStageId(caseId, workflowStageId)
                .orElseThrow().getStatus().name();
    }
}
