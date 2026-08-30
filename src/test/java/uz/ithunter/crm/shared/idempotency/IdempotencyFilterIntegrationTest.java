package uz.ithunter.crm.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.application.Service;
import uz.ithunter.crm.casemodule.AbstractCaseIntegrationTest;
import uz.ithunter.crm.casemodule.dto.CaseResponse;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;

/**
 * TEST_MATRIX.md C-05: "a replayed POST /applications/{id}/register with the same Idempotency-Key
 * returns the stored response and creates one case" (1.4). This is a SEQUENTIAL replay, not a race
 * - see {@link IdempotencyFilter}'s own javadoc for why that is sufficient here.
 */
class IdempotencyFilterIntegrationTest extends AbstractCaseIntegrationTest {

    private UUID registrationReadyApplication(String prefix, User departmentHead) throws Exception {
        Department department = seedDepartment(prefix);
        Service service = seedService();
        Workflow workflow = seedWorkflow(service.getId(), department.getId(), 10);
        var stage = seedStage(workflow, "PC", StageType.PRIMARY_CHECK, 1, "DOCUMENTS_UNDER_REVIEW",
                department.getId(), null, true);
        seedTransition(workflow, null, stage.getId(), ConditionType.ALWAYS, null, 1);

        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");
        return submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
    }

    @Test
    void aReplayedRegisterWithTheSameKeyReturnsTheStoredResponseAndCreatesOneCase() throws Exception {
        Department department = seedDepartment("IDEMP1");
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        UUID applicationId = registrationReadyApplication("IDEMP1", departmentHead);
        String idempotencyKey = "register-" + unique();
        String body = objectMapper.writeValueAsString(new uz.ithunter.crm.casemodule.dto.RegisterApplicationRequest(null));

        var firstResult = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        CaseResponse first = objectMapper.readValue(firstResult.getResponse().getContentAsString(), CaseResponse.class);

        var replayResult = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        CaseResponse replay = objectMapper.readValue(replayResult.getResponse().getContentAsString(), CaseResponse.class);

        assertThat(replay.id()).isEqualTo(first.id());
        Integer caseCount = jdbc.queryForObject(
                "SELECT count(*) FROM electronic_case WHERE application_id = ?", Integer.class, applicationId);
        assertThat(caseCount).isEqualTo(1);
    }

    @Test
    void replayingTheSameKeyWithADifferentBodyIsRejected() throws Exception {
        Department department = seedDepartment("IDEMP2");
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        UUID applicationId = registrationReadyApplication("IDEMP2", departmentHead);
        String idempotencyKey = "register-" + unique();

        mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new uz.ithunter.crm.casemodule.dto.RegisterApplicationRequest("first note"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new uz.ithunter.crm.casemodule.dto.RegisterApplicationRequest("a different note"))))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void requestsWithoutAnIdempotencyKeyAreUnaffected() throws Exception {
        Department department = seedDepartment("IDEMP3");
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        UUID applicationId = registrationReadyApplication("IDEMP3", departmentHead);

        mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new uz.ithunter.crm.casemodule.dto.RegisterApplicationRequest(null))))
                .andExpect(status().isCreated());

        // Without a key, a second attempt hits the application's own state guard (it is no longer
        // SUBMITTED), not the idempotency filter - it must NOT silently succeed a second time.
        mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new uz.ithunter.crm.casemodule.dto.RegisterApplicationRequest(null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_STATE_TRANSITION"));
    }
}
