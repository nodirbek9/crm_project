package uz.ithunter.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.AbstractIntegrationTest;

/**
 * Phase 13 DoD: "demo/V900 produces a fully walkable scenario" - not by clicking through Swagger
 * by hand, but by driving the exact same HTTP endpoints, as the exact demo users/credentials, all
 * the way from application to a COMPLETED case. This is the automated, repeatable version of the
 * demo script in FINAL_IMPLEMENTATION_ORDER.md; the README points here for anyone who wants to
 * verify the walkthrough without clicking through it by hand.
 *
 * <p>Uses ONLY {@code db/demo/V900}'s seeded users/workflow/price rules (loaded by the "test"
 * profile alongside every other integration test) and the real {@code /api/auth/login} endpoint -
 * proving the demo credentials documented in the README actually work, not a shortcut token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoScenarioWalkthroughTest extends AbstractIntegrationTest {

    private static final String DEMO_PASSWORD = "Demo12345!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", DEMO_PASSWORD));
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(json, Map.class).get("accessToken");
    }

    private UUID taskFor(String stageCode, UUID caseId) {
        return jdbc.queryForObject(
                "SELECT t.id FROM task t JOIN workflow_stage ws ON t.workflow_stage_id = ws.id "
                        + "WHERE t.case_id = ? AND ws.code = ?", UUID.class, caseId, stageCode);
    }

    private void assignStartSubmitComplete(String taskAuth, String assignToken, UUID taskId, UUID assigneeUserId)
            throws Exception {
        mockMvc.perform(post("/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + taskAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", assigneeUserId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + assignToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tasks/" + taskId + "/results")
                        .header("Authorization", "Bearer " + assignToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("payload", "{\"note\":\"demo\"}", "summary", "Demo walkthrough result"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + assignToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void demoUsersWalkTheWholeRouteFromApplicationToCompletedCase() throws Exception {
        String applicantToken = login("applicant@example.com");
        String depHeadMainToken = login("depthead.main@example.com");
        String depHeadLabToken = login("depthead.lab@example.com");
        String specialist1Token = login("specialist1@example.com");
        String specialist2Token = login("specialist2@example.com");
        String accountantToken = login("accountant@example.com");
        String headToken = login("head@example.com");

        UUID specialist1Id = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE email = 'specialist1@example.com'", UUID.class);
        UUID specialist2Id = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE email = 'specialist2@example.com'", UUID.class);
        UUID serviceId = jdbc.queryForObject(
                "SELECT id FROM service WHERE code = 'WASTE_CERTIFICATION'", UUID.class);

        // ---- 0. bootstrap: grant DEPARTMENT_HEAD the APPLICATION:EDIT/CASE:EDIT it needs to
        // register a case, entirely through the admin API (ASSUMPTIONS A30: SECURITY_SPEC.md's own
        // table gives these to no role, by design - a real deployment configures this once via the
        // admin endpoint, not by editing seed data. This is "no manual DB work", just one API call
        // an admin makes before anyone else can use the system.) ----
        String adminToken = login("admin@example.com");
        String currentGrantsJson = mockMvc.perform(get("/api/admin/roles/DEPARTMENT_HEAD/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> originalGrants = new java.util.ArrayList<>(
                (List<String>) objectMapper.readValue(currentGrantsJson, Map.class).get("permissionCodes"));
        List<String> expandedGrants = new java.util.ArrayList<>(originalGrants);
        expandedGrants.add("APPLICATION:EDIT");
        expandedGrants.add("CASE:EDIT");
        patchDepartmentHeadPermissions(adminToken, expandedGrants);
        try {
            walkTheDemoRoute(applicantToken, depHeadMainToken, depHeadLabToken, specialist1Token,
                    specialist2Token, accountantToken, headToken, specialist1Id, specialist2Id, serviceId);
        } finally {
            // SecurityGrantMatrixTest asserts an EXACT grant set per role in this same shared
            // container - leaving this bootstrap grant in place would break it for the rest of the
            // mvn verify run, order-dependently. Restore exactly what was there before.
            patchDepartmentHeadPermissions(adminToken, originalGrants);
        }
    }

    private void patchDepartmentHeadPermissions(String adminToken, List<String> permissionCodes) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/roles/DEPARTMENT_HEAD/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("permissionCodes", permissionCodes))))
                .andExpect(status().isOk());
    }

    private void walkTheDemoRoute(String applicantToken, String depHeadMainToken, String depHeadLabToken,
            String specialist1Token, String specialist2Token, String accountantToken, String headToken,
            UUID specialist1Id, UUID specialist2Id, UUID serviceId) throws Exception {
        // ---- 1. applicant creates and submits an application with 2 case items ----
        String createBody = objectMapper.writeValueAsString(Map.of(
                "serviceId", serviceId, "submissionChannel", "PERSONAL_CABINET",
                "formData", Map.of(),
                "items", List.of(
                        Map.of("name", "Waste sample A", "quantity", 3, "unit", "PCS", "objectAddress", "Tashkent"),
                        Map.of("name", "Waste sample B", "quantity", 1, "unit", "KG", "objectAddress", "Tashkent"))));
        String createdJson = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID applicationId = UUID.fromString((String) objectMapper.readValue(createdJson, Map.class).get("id"));
        mockMvc.perform(post("/api/applications/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk());

        // ---- 2. depthead.main registers -> case appears ----
        var registerResult = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + depHeadMainToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID caseId = UUID.fromString(registerResult.getResponse().getHeader("Location").replace("/api/cases/", ""));

        // ---- 3. specialist1 primary check -> GREEN/ACCEPTED ----
        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + specialist1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());

        // ---- 4. accountant: EXPEDITED mode -> recalculated price -> confirm with a DIFFERENT
        // amount + reason -> record contract -> confirm full payment ----
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/processing-mode")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "EXPEDITED"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/calculate")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk());
        String calculatedJson = mockMvc.perform(get("/api/accounting/cases/" + caseId + "/price")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        BigDecimal calculated = new BigDecimal(objectMapper.readValue(calculatedJson, Map.class)
                .get("calculatedTotal").toString());
        BigDecimal actualAmount = calculated.add(new BigDecimal("500000"));
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/price/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actualAmount", actualAmount, "reason", "Demo walkthrough negotiated discount"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/contract")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contractNumber", "DEMO-CTR-" + caseId.toString().substring(0, 8),
                                "contractDate", "2026-08-30", "sentChannel", "DIDOX",
                                "sentAt", "2026-08-30T10:00:00Z"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accounting/cases/" + caseId + "/payment/confirm")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", actualAmount))))
                .andExpect(status().isOk());

        // ---- 5. IDENTIFICATION: depthead.main assigns to specialist1, who submits + completes ----
        UUID identificationTask = taskFor("IDENTIFICATION", caseId);
        assignStartSubmitComplete(depHeadMainToken, specialist1Token, identificationTask, specialist1Id);

        // ---- 6. three parallel tasks open; complete LABORATORY + EXPERT_REVIEW, leave AUDIT open ----
        UUID labTask = taskFor("LABORATORY", caseId);
        assignStartSubmitComplete(depHeadLabToken, specialist2Token, labTask, specialist2Id);
        UUID expertTask = taskFor("EXPERT_REVIEW", caseId);
        assignStartSubmitComplete(depHeadMainToken, specialist1Token, expertTask, specialist1Id);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM case_stage cs JOIN workflow_stage ws ON cs.workflow_stage_id = ws.id "
                        + "WHERE cs.case_id = ? AND ws.code = 'AUDIT'", String.class, caseId))
                .isEqualTo("ACTIVE"); // optional AUDIT never blocks FINAL_REVIEW (spec 7.14)
        assertThat(jdbc.queryForObject(
                "SELECT status FROM case_stage cs JOIN workflow_stage ws ON cs.workflow_stage_id = ws.id "
                        + "WHERE cs.case_id = ? AND ws.code = 'FINAL_REVIEW'", String.class, caseId))
                .isEqualTo("ACTIVE");

        // ---- 7. FINAL_REVIEW completed by specialist1, then ENDORSEMENT: parallel round, two
        // required participants, both approve ----
        UUID finalReviewTask = taskFor("FINAL_REVIEW", caseId);
        assignStartSubmitComplete(depHeadMainToken, specialist1Token, finalReviewTask, specialist1Id);

        String docJson = mockMvc.perform(post("/api/cases/" + caseId + "/documents")
                        .header("Authorization", "Bearer " + specialist1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("documentType", "CERTIFICATE", "title", "Demo waste certificate"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readValue(docJson, Map.class).get("id").toString());
        mockMvc.perform(post("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + specialist1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentRef", "s3://demo/" + documentId, "contentHash", "e".repeat(64),
                                "fileName", "certificate.pdf", "mimeType", "application/pdf", "sizeBytes", 2048))))
                .andExpect(status().isCreated());

        UUID depHeadMainId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE email = 'depthead.main@example.com'", UUID.class);
        String roundJson = mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/approval-rounds")
                        .header("Authorization", "Bearer " + depHeadMainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "PARALLEL", "participants", List.of(
                                Map.of("kind", "USER", "userId", depHeadMainId, "required", true, "sequenceNo", 1),
                                Map.of("kind", "USER", "userId", specialist1Id, "required", true, "sequenceNo", 2))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> round = objectMapper.readValue(roundJson, Map.class);
        List<?> tasks = (List<?>) round.get("tasks");
        UUID depHeadApprovalTask = tasks.stream().map(t -> (Map<?, ?>) t)
                .filter(t -> depHeadMainId.toString().equals(t.get("participantUserId")))
                .findFirst().map(t -> UUID.fromString(t.get("id").toString())).orElseThrow();
        UUID specialistApprovalTask = tasks.stream().map(t -> (Map<?, ?>) t)
                .filter(t -> specialist1Id.toString().equals(t.get("participantUserId")))
                .findFirst().map(t -> UUID.fromString(t.get("id").toString())).orElseThrow();

        mockMvc.perform(post("/api/approval-tasks/" + depHeadApprovalTask + "/approve")
                        .header("Authorization", "Bearer " + depHeadMainToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/approval-tasks/" + specialistApprovalTask + "/approve")
                        .header("Authorization", "Bearer " + specialist1Token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // ---- 8. head signs -> case COMPLETED ----
        mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/sign")
                        .header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM electronic_case WHERE id = ?", String.class, caseId))
                .isEqualTo("COMPLETED");

        // ---- 9. performed-works: each work counted once ----
        String performedWorksJson = mockMvc.perform(get("/api/cases/" + caseId + "/performed-works")
                        .header("Authorization", "Bearer " + depHeadMainToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<?> performedWorks = objectMapper.readValue(performedWorksJson, List.class);
        assertThat(performedWorks).isNotEmpty();
        List<?> workTypeIds = performedWorks.stream().map(w -> ((Map<?, ?>) w).get("workTypeId")).toList();
        assertThat(workTypeIds).doesNotHaveDuplicates();

        // ---- 10. audit chain intact for this case's own rows ----
        Long caseFirstSeq = jdbc.queryForObject(
                "SELECT seq FROM audit_log WHERE case_id = ? ORDER BY seq ASC LIMIT 1", Long.class, caseId);
        Integer breaks = jdbc.queryForObject(
                "SELECT count(*) FROM verify_audit_chain() WHERE broken_seq >= ?", Integer.class, caseFirstSeq);
        assertThat(breaks).isZero();

        // ---- 11. applicant tracking view: external stages/contract/payment only ----
        String trackingJson = mockMvc.perform(get("/api/cases/" + caseId + "/tracking")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> tracking = objectMapper.readValue(trackingJson, Map.class);
        assertThat(tracking.containsKey("performedWorks")).isFalse();
        assertThat(tracking.containsKey("internalComments")).isFalse();
        assertThat(tracking.containsKey("auditLog")).isFalse();
    }
}
