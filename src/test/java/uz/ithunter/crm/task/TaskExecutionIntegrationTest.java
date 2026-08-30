package uz.ithunter.crm.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.ithunter.crm.casemodule.AbstractCaseIntegrationTest;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;

class TaskExecutionIntegrationTest extends AbstractCaseIntegrationTest {

    private UUID serviceId;
    private UUID mainDeptId;
    private UUID otherDeptId;
    private UUID applicantUserId;
    private UUID specialistUserId;
    private UUID deptHeadUserId;
    private UUID otherDeptHeadUserId;

    private Workflow workflow;
    private WorkflowStage splitStage;
    private WorkflowStage p1Stage;
    private WorkflowStage p2Stage;
    private WorkflowStage joinStage;

    @BeforeEach
    void setupWorkflow() {
        serviceId = seedService().getId();
        mainDeptId = seedDepartment("MAIN").getId();
        otherDeptId = seedDepartment("OTHER").getId();

        // Users
        UUID applicantId = seedApplicant().getId();
        applicantUserId = seedApplicantUser(seedApplicant()).getId();
        specialistUserId = seedStaffUser(RoleCode.SPECIALIST, mainDeptId).getId();
        deptHeadUserId = seedStaffUser(RoleCode.DEPARTMENT_HEAD, mainDeptId).getId();
        otherDeptHeadUserId = seedStaffUser(RoleCode.DEPARTMENT_HEAD, otherDeptId).getId();

        // Workflow config for execution parallel gate
        workflow = seedWorkflow(serviceId, mainDeptId, 15);
        
        // Root -> Split -> P1 & P2 -> Join
        splitStage = seedStage(workflow, "SPLIT", StageType.PRIMARY_CHECK, 1, "DOCUMENTS_UNDER_REVIEW", mainDeptId, null, true);
        
        p1Stage = seedStage(workflow, "P1", StageType.EXECUTION, 2, "WORK_IN_PROGRESS", mainDeptId, "GROUP1", true);
        p1Stage.setRequiresResult(true);
        p1Stage.setApprovalRequired(true);
        jdbc.update("UPDATE workflow_stage SET requires_result = true, approval_required = true, approval_mode = 'SEQUENTIAL' WHERE id = ?", p1Stage.getId());
        
        p2Stage = seedStage(workflow, "P2", StageType.EXECUTION, 3, "LAB_TESTS_IN_PROGRESS", otherDeptId, "GROUP1", true);
        
        joinStage = seedStage(workflow, "JOIN", StageType.FINAL_REVIEW, 4, "FINAL_DOCUMENT_PREPARING", mainDeptId, null, true);

        seedTransition(workflow, splitStage.getId(), p1Stage.getId(), ConditionType.ALWAYS, null, 1);
        seedTransition(workflow, splitStage.getId(), p2Stage.getId(), ConditionType.ALWAYS, null, 2);
        
        seedTransition(workflow, p1Stage.getId(), joinStage.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "GROUP1", 1);
        seedTransition(workflow, p2Stage.getId(), joinStage.getId(), ConditionType.ALL_REQUIRED_PARALLEL_TASKS_DONE, "GROUP1", 2);

        jdbc.update("UPDATE workflow SET status = 'ACTIVE' WHERE id = ?", workflow.getId());
    }

    @Test
    void executeParallelTasksAndAdvance() throws Exception {
        // We need User objects for token()
        uz.ithunter.crm.user.User applicantUserObj = userRepository.findById(applicantUserId).orElseThrow();
        uz.ithunter.crm.user.User deptHeadUserObj = userRepository.findById(deptHeadUserId).orElseThrow();
        uz.ithunter.crm.user.User otherDeptHeadUserObj = userRepository.findById(otherDeptHeadUserId).orElseThrow();
        uz.ithunter.crm.user.User specialistUserObj = userRepository.findById(specialistUserId).orElseThrow();

        // 1. Submit application and reach SPLIT
        grant(RoleCode.APPLICANT, "APPLICATION:CREATE");
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUserObj, serviceId, Map.of(), oneItem());
        
        String location = mockMvc.perform(post("/api/applications/{id}/register", applicationId)
                .header("Authorization", "Bearer " + token(deptHeadUserObj))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        
        UUID caseId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        
        // Manually complete SPLIT to activate P1 and P2
        UUID splitCaseStage = jdbc.queryForObject("SELECT id FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?", UUID.class, caseId, splitStage.getId());
        jdbc.update("UPDATE case_stage SET status = 'ACTIVE', activated_at = NOW() WHERE id = ?", splitCaseStage);
        
        // Advance from split to activate P1 and P2 tasks
        UUID p1CaseStage = jdbc.queryForObject("SELECT id FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?", UUID.class, caseId, p1Stage.getId());
        jdbc.update("UPDATE case_stage SET status = 'ACTIVE', activated_at = NOW() WHERE id = ?", p1CaseStage);
        UUID p2CaseStage = jdbc.queryForObject("SELECT id FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?", UUID.class, caseId, p2Stage.getId());
        jdbc.update("UPDATE case_stage SET status = 'ACTIVE', activated_at = NOW() WHERE id = ?", p2CaseStage);

        jdbc.update("INSERT INTO task (case_id, case_stage_id, workflow_stage_id, title, assigned_department_id, status) VALUES (?, ?, ?, 'P1', ?, 'CREATED')", caseId, p1CaseStage, p1Stage.getId(), mainDeptId);
        UUID t1 = jdbc.queryForObject("SELECT id FROM task WHERE case_stage_id = ?", UUID.class, p1CaseStage);

        jdbc.update("INSERT INTO task (case_id, case_stage_id, workflow_stage_id, title, assigned_department_id, status) VALUES (?, ?, ?, 'P2', ?, 'CREATED')", caseId, p2CaseStage, p2Stage.getId(), otherDeptId);
        UUID t2 = jdbc.queryForObject("SELECT id FROM task WHERE case_stage_id = ?", UUID.class, p2CaseStage);

        // Test S-09: otherDeptHead cannot assign t1
        grant(RoleCode.DEPARTMENT_HEAD, "TASK:EDIT");
        mockMvc.perform(post("/tasks/{id}/assign", t1)
                .header("Authorization", "Bearer " + token(otherDeptHeadUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", specialistUserId))))
                .andExpect(status().isForbidden());

        // Assign t1 properly
        mockMvc.perform(post("/tasks/{id}/assign", t1)
                .header("Authorization", "Bearer " + token(deptHeadUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", specialistUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // Test S-08: other specialist cannot start t1
        uz.ithunter.crm.user.User otherSpecObj = seedStaffUser(RoleCode.SPECIALIST, mainDeptId);
        UUID otherSpec = otherSpecObj.getId();
        mockMvc.perform(post("/tasks/{id}/start", t1)
                .header("Authorization", "Bearer " + token(otherSpecObj))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Start t1
        mockMvc.perform(post("/tasks/{id}/start", t1)
                .header("Authorization", "Bearer " + token(specialistUserObj))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Complete without result should fail (W-05)
        mockMvc.perform(post("/tasks/{id}/complete", t1)
                .header("Authorization", "Bearer " + token(specialistUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("version", 0))))
                .andExpect(status().isUnprocessableEntity()) // 422
                .andExpect(jsonPath("$.code").value("RESULT_REQUIRED"));

        // Submit result
        mockMvc.perform(post("/tasks/{id}/results", t1)
                .header("Authorization", "Bearer " + token(specialistUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("payload", "{\"doc\":\"123\"}", "summary", "Done"))))
                .andExpect(status().isCreated());

        // Complete without approval should fail because approvalRequired = true
        mockMvc.perform(post("/tasks/{id}/complete", t1)
                .header("Authorization", "Bearer " + token(specialistUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("version", 0))))
                .andExpect(status().isUnprocessableEntity()) // 422
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"));

        // Approve result
        grant(RoleCode.DEPARTMENT_HEAD, "TASK:APPROVE");
        mockMvc.perform(post("/tasks/{id}/approve-result", t1)
                .header("Authorization", "Bearer " + token(deptHeadUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        // Complete t1
        mockMvc.perform(post("/tasks/{id}/complete", t1)
                .header("Authorization", "Bearer " + token(specialistUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Check if join stage activated? No, t2 is not completed yet.
        String joinStatus = jdbc.queryForObject("SELECT status FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?", String.class, caseId, joinStage.getId());
        assertThat(joinStatus).isEqualTo("PENDING");

        // Fake complete P2
        jdbc.update("UPDATE task SET assigned_user_id = ?, assigned_by_id = ?, assigned_at = NOW(), status = 'COMPLETED', completed_at = NOW() WHERE id = ?", deptHeadUserObj.getId(), deptHeadUserObj.getId(), t2);
        jdbc.update("UPDATE case_stage SET status = 'COMPLETED', completed_at = now() WHERE id = ?", p2CaseStage);

        // Re-completing t1 should be a no-op and NOT advance twice
        mockMvc.perform(post("/tasks/{id}/complete", t1)
                .header("Authorization", "Bearer " + token(specialistUserObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("version", 1))))
                .andExpect(status().isOk());

        // Now both P1 and P2 are COMPLETED. Note: Since we mocked the engine advance previously by updating DB manually, the advance might not run from t1's complete.
        // Actually, our engine's `completeStage` calls `advance()`. If we complete t2 by API, it will check the gate and advance. Let's do it via API!
        // Revert DB hack for t2:
        jdbc.update("UPDATE task SET status = 'IN_PROGRESS', assigned_user_id = ? WHERE id = ?", otherSpec, t2);
        jdbc.update("UPDATE case_stage SET status = 'ACTIVE', completed_at = null WHERE id = ?", p2CaseStage);

        mockMvc.perform(post("/tasks/{id}/complete", t2)
                .header("Authorization", "Bearer " + token(otherSpecObj))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("version", 0))))
                .andExpect(status().isOk());

        // Now JOIN should be ACTIVE because ALL_REQUIRED_PARALLEL_TASKS_DONE evaluates to true
        joinStatus = jdbc.queryForObject("SELECT status FROM case_stage WHERE case_id = ? AND workflow_stage_id = ?", String.class, caseId, joinStage.getId());
        assertThat(joinStatus).isEqualTo("ACTIVE");
    }
}
