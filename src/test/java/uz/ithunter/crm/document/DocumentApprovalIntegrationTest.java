package uz.ithunter.crm.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.casemodule.AbstractCaseIntegrationTest;
import uz.ithunter.crm.casemodule.CaseStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowStage;

/**
 * Phase 10 DoD (FINAL_IMPLEMENTATION_ORDER.md): D-01 … D-08, S-10.
 */
class DocumentApprovalIntegrationTest extends AbstractCaseIntegrationTest {

    private static final String HASH_1 = "a".repeat(64);
    private static final String HASH_2 = "b".repeat(64);
    private static final String HASH_3 = "c".repeat(64);

    private record Route(Workflow workflow, WorkflowStage endorsement, WorkflowStage signing,
            WorkflowStage completion) {
    }

    /** PC (entry) -> ENDORSEMENT -> SIGNING -> DONE, all ALWAYS transitions. */
    private Route seedRoute(uz.ithunter.crm.application.Service service, Department department) {
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
        return new Route(workflow, endorsement, signing, completion);
    }

    private record Fixture(Route route, UUID caseId, User departmentHead, User specialist, User head) {
    }

    private Fixture registerAndAcceptPrimaryCheck(String prefix) throws Exception {
        Department department = seedDepartment(prefix);
        uz.ithunter.crm.application.Service service = seedService();
        Route route = seedRoute(service, department);
        Applicant applicant = seedApplicant();
        User applicantUser = seedApplicantUser(applicant);
        User departmentHead = seedStaffUser(RoleCode.DEPARTMENT_HEAD, department.getId());
        User specialist = seedStaffUser(RoleCode.SPECIALIST, department.getId());
        User head = seedStaffUser(RoleCode.HEAD_OF_CERTIFICATION_BODY, department.getId());
        grant(RoleCode.DEPARTMENT_HEAD, "APPLICATION:EDIT");

        UUID applicationId = submittedApplication(applicantUser, service.getId(), Map.of(), oneItem());
        var result = mockMvc.perform(post("/api/applications/" + applicationId + "/register")
                        .header("Authorization", "Bearer " + token(departmentHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID caseId = UUID.fromString(result.getResponse().getHeader("Location")
                .replace("/api/cases/", ""));

        mockMvc.perform(post("/api/cases/" + caseId + "/primary-check")
                        .header("Authorization", "Bearer " + token(specialist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("category", "GREEN", "decision", "ACCEPTED"))))
                .andExpect(status().isOk());
        return new Fixture(route, caseId, departmentHead, specialist, head);
    }

    private UUID createDocument(Fixture fx) throws Exception {
        String json = mockMvc.perform(post("/api/cases/" + fx.caseId() + "/documents")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("documentType", "CERTIFICATE", "title", "Test certificate"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(((Map<?, ?>) objectMapper.readValue(json, Map.class)).get("id").toString());
    }

    private void createVersion1(Fixture fx, UUID documentId) throws Exception {
        mockMvc.perform(post("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentRef", "s3://demo/" + unique(), "contentHash", HASH_1,
                                "fileName", "cert.pdf", "mimeType", "application/pdf", "sizeBytes", 1024))))
                .andExpect(status().isCreated());
    }

    private String startRound(Fixture fx, UUID documentId, List<Map<String, Object>> participants) throws Exception {
        return startRound(fx, documentId, 1, participants);
    }

    private String startRound(Fixture fx, UUID documentId, int versionNo, List<Map<String, Object>> participants)
            throws Exception {
        return mockMvc.perform(post("/api/documents/" + documentId + "/versions/" + versionNo + "/approval-rounds")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mode", "PARALLEL", "participants", participants))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private List<Map<String, Object>> twoRequiredUserParticipants(Fixture fx) {
        return List.of(
                Map.of("kind", "USER", "userId", fx.departmentHead().getId(), "required", true, "sequenceNo", 1),
                Map.of("kind", "USER", "userId", fx.specialist().getId(), "required", true, "sequenceNo", 2));
    }

    // ---- D-03: an endorsement round fans out one ApprovalTask per participant ----

    @Test
    void startingARoundFansOutOneApprovalTaskPerParticipant() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D03");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);

        String roundJson = startRound(fx, documentId, twoRequiredUserParticipants(fx));
        Map<?, ?> round = objectMapper.readValue(roundJson, Map.class);
        List<?> tasks = (List<?>) round.get("tasks");
        assertThat(tasks).hasSize(2);
    }

    // ---- D-05: two required participants, one approval does not complete the round ----

    @Test
    void oneApprovalOfTwoRequiredDoesNotCompleteTheRound() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D05");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);
        String roundJson = startRound(fx, documentId, twoRequiredUserParticipants(fx));
        Map<?, ?> round = objectMapper.readValue(roundJson, Map.class);
        UUID deptHeadTaskId = taskIdFor(round, fx.departmentHead().getId());

        mockMvc.perform(post("/api/approval-tasks/" + deptHeadTaskId + "/approve")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String roundId = round.get("id").toString();
        String stillOpenJson = mockMvc.perform(get("/api/approval-rounds/" + roundId)
                        .header("Authorization", "Bearer " + token(fx.departmentHead())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> stillOpen = objectMapper.readValue(stillOpenJson, Map.class);
        assertThat(stillOpen.get("status")).isEqualTo("IN_PROGRESS");
    }

    // ---- D-06: all required approvals complete the round, ENDORSE the version, advance the case ----

    @Test
    void allRequiredApprovalsCompleteTheRoundEndorseTheVersionAndAdvanceTheCase() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D06");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);
        String roundJson = startRound(fx, documentId, twoRequiredUserParticipants(fx));
        Map<?, ?> round = objectMapper.readValue(roundJson, Map.class);
        UUID deptHeadTaskId = taskIdFor(round, fx.departmentHead().getId());
        UUID specialistTaskId = taskIdFor(round, fx.specialist().getId());

        approve(fx.departmentHead(), deptHeadTaskId);
        approve(fx.specialist(), specialistTaskId);

        String versionJson = mockMvc.perform(get("/api/documents/" + documentId + "/versions/1")
                        .header("Authorization", "Bearer " + token(fx.departmentHead())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(versionJson, Map.class).get("status")).isEqualTo("ENDORSED");

        // The case advanced out of ENDORSEMENT into SIGNING.
        var signingStage = caseStageRepository.findByCaseIdAndWorkflowStageId(fx.caseId(), fx.route().signing().getId())
                .orElseThrow();
        assertThat(signingStage.getStatus().name()).isEqualTo("ACTIVE");
    }

    private void approve(User approver, UUID taskId) throws Exception {
        mockMvc.perform(post("/api/approval-tasks/" + taskId + "/approve")
                        .header("Authorization", "Bearer " + token(approver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private UUID taskIdFor(Map<?, ?> round, UUID userId) {
        List<?> tasks = (List<?>) round.get("tasks");
        return tasks.stream()
                .map(t -> (Map<?, ?>) t)
                .filter(t -> userId.toString().equals(t.get("participantUserId")))
                .findFirst()
                .map(t -> UUID.fromString(t.get("id").toString()))
                .orElseThrow();
    }

    // ---- D-04: rejecting without a comment -> 400; the DB CHECK also rejects it ----

    @Test
    void rejectingWithoutACommentIsRejectedByApiAndDb() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D04");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);
        String roundJson = startRound(fx, documentId, twoRequiredUserParticipants(fx));
        Map<?, ?> round = objectMapper.readValue(roundJson, Map.class);
        UUID deptHeadTaskId = taskIdFor(round, fx.departmentHead().getId());

        mockMvc.perform(post("/api/approval-tasks/" + deptHeadTaskId + "/reject")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPROVAL_COMMENT_REQUIRED"));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE approval_task SET status = 'REJECTED', comment = NULL, "
                        + "decided_by_id = ?, decided_at = now() WHERE id = ?",
                fx.departmentHead().getId(), deptHeadTaskId))
                .isInstanceOf(DataAccessException.class);
    }

    // ---- D-07: a rejection produces a new version and a new round; old remarks stay bound ----

    @Test
    void aRejectionProducesANewVersionAndANewRoundWithOldRemarksIntact() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D07");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);
        String roundJson = startRound(fx, documentId, twoRequiredUserParticipants(fx));
        Map<?, ?> round = objectMapper.readValue(roundJson, Map.class);
        UUID deptHeadTaskId = taskIdFor(round, fx.departmentHead().getId());

        mockMvc.perform(post("/api/approval-tasks/" + deptHeadTaskId + "/reject")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Missing signature block"))))
                .andExpect(status().isOk());

        // A new version (2) is creatable now that the round on v1 is closed.
        mockMvc.perform(post("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentRef", "s3://demo/" + unique(), "contentHash", HASH_2,
                                "fileName", "cert-v2.pdf", "mimeType", "application/pdf", "sizeBytes", 2048,
                                "revisionReason", "Added the missing signature block"))))
                .andExpect(status().isCreated());

        String roundJson2 = startRound(fx, documentId, 2, List.of(
                Map.of("kind", "USER", "userId", fx.departmentHead().getId(), "required", true, "sequenceNo", 1)));
        Map<?, ?> round2 = objectMapper.readValue(roundJson2, Map.class);
        assertThat(round2.get("roundNo")).isEqualTo(1); // fresh round numbering per version
        assertThat(round2.get("id")).isNotEqualTo(round.get("id"));

        // D-01 applies here too: creating v2 supersedes v1 regardless of why a new version was
        // needed, so v1's own status moves from REJECTED to SUPERSEDED - it is no longer the live
        // version. What D-07 actually promises is that the REJECTION ITSELF (the round's outcome
        // and the participant's remark) stays bound to the old version/round, which is what the
        // approval-round assertions below check, not the version's own mutable status field.
        String v1Json = mockMvc.perform(get("/api/documents/" + documentId + "/versions/1")
                        .header("Authorization", "Bearer " + token(fx.departmentHead())))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(v1Json, Map.class).get("status")).isEqualTo("SUPERSEDED");
        String oldRoundJson = mockMvc.perform(get("/api/approval-rounds/" + round.get("id"))
                        .header("Authorization", "Bearer " + token(fx.departmentHead())))
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> oldRound = objectMapper.readValue(oldRoundJson, Map.class);
        assertThat(oldRound.get("status")).isEqualTo("COMPLETED_REJECTED");
        Map<?, ?> oldTask = taskFor(oldRound, fx.departmentHead().getId());
        assertThat(oldTask.get("comment")).isEqualTo("Missing signature block");
        assertThat(oldTask.get("status")).isEqualTo("REJECTED");
    }

    private Map<?, ?> taskFor(Map<?, ?> round, UUID userId) {
        List<?> tasks = (List<?>) round.get("tasks");
        return tasks.stream().map(t -> (Map<?, ?>) t)
                .filter(t -> userId.toString().equals(t.get("participantUserId")))
                .findFirst().orElseThrow();
    }

    // ---- D-01: creating a second version supersedes the first; both remain retrievable ----

    @Test
    void creatingASecondVersionSupersedesTheFirstBothRemainRetrievable() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D01");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);

        mockMvc.perform(post("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentRef", "s3://demo/" + unique(), "contentHash", HASH_3,
                                "fileName", "cert-v2.pdf", "mimeType", "application/pdf", "sizeBytes", 2048,
                                "revisionReason", "Manual correction"))))
                .andExpect(status().isCreated());

        String v1Json = mockMvc.perform(get("/api/documents/" + documentId + "/versions/1")
                        .header("Authorization", "Bearer " + token(fx.departmentHead())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> v1 = objectMapper.readValue(v1Json, Map.class);
        assertThat(v1.get("status")).isEqualTo("SUPERSEDED");
        assertThat(v1.get("contentHash")).isEqualTo(HASH_1);

        String v2Json = mockMvc.perform(get("/api/documents/" + documentId + "/versions/2")
                        .header("Authorization", "Bearer " + token(fx.departmentHead())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> v2 = objectMapper.readValue(v2Json, Map.class);
        assertThat(v2.get("contentHash")).isEqualTo(HASH_3);
        assertThat(v2.get("supersedesId")).isEqualTo(v1.get("id"));
    }

    // ---- D-02: a direct UPDATE of document_version.content_ref is rejected by the trigger ----

    @Test
    void directUpdateOfContentRefIsRejectedByTheTrigger() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D02");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);
        UUID versionId = jdbc.queryForObject(
                "SELECT id FROM document_version WHERE document_id = ? AND version_no = 1",
                UUID.class, documentId);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE document_version SET content_ref = 'tampered' WHERE id = ?", versionId))
                .isInstanceOf(DataAccessException.class);
    }

    // ---- D-08 / S-10: signing gate and who may sign ----

    @Test
    void signingBeforeApprovalCompletesIsRejected() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("D08");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);
        // No approval round started at all - version status is still DRAFT.

        mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/sign")
                        .header("Authorization", "Bearer " + token(fx.head()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_COMPLETED"));
    }

    @Test
    void onlyHeadOfCertificationBodyCanSignDepartmentHeadAndAdminGet403() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("S10");
        User admin = seedStaffUser(RoleCode.ADMIN, seedDepartment("S10ADMIN").getId());

        // Neither role holds DOCUMENT:SIGN in the seeded matrix (only HEAD_OF_CERTIFICATION_BODY
        // does), so @PreAuthorize alone already blocks both with a generic 403 PERMISSION_DENIED.
        // Granting it temporarily reaches SigningService.sign()'s own explicit role check - the
        // defense-in-depth layer S-10 actually wants to see the specific SIGN_NOT_PERMITTED code
        // from - same technique Phase 8 used for APPLICANT_CANNOT_SET_MODE.
        grant(RoleCode.DEPARTMENT_HEAD, "DOCUMENT:SIGN");
        mockMvc.perform(post("/api/documents/" + UUID.randomUUID() + "/versions/1/sign")
                        .header("Authorization", "Bearer " + token(fx.departmentHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGN_NOT_PERMITTED"));

        grant(RoleCode.ADMIN, "DOCUMENT:SIGN");
        mockMvc.perform(post("/api/documents/" + UUID.randomUUID() + "/versions/1/sign")
                        .header("Authorization", "Bearer " + token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGN_NOT_PERMITTED"));
    }

    @Test
    void signingCompletesTheDocumentAdvancesTheCaseAndTheSignedVersionBecomesImmutable() throws Exception {
        Fixture fx = registerAndAcceptPrimaryCheck("SIGNOK");
        UUID documentId = createDocument(fx);
        createVersion1(fx, documentId);
        String roundJson = startRound(fx, documentId, twoRequiredUserParticipants(fx));
        Map<?, ?> round = objectMapper.readValue(roundJson, Map.class);
        approve(fx.departmentHead(), taskIdFor(round, fx.departmentHead().getId()));
        approve(fx.specialist(), taskIdFor(round, fx.specialist().getId()));

        mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/sign")
                        .header("Authorization", "Bearer " + token(fx.head()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "Approved and signed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));

        ElectronicCase electronicCase = electronicCaseRepository.findById(fx.caseId()).orElseThrow();
        assertThat(electronicCase.getStatus()).isEqualTo(CaseStatus.COMPLETED);

        // D-08: a signed version cannot change status - the DB trigger rejects it even directly.
        UUID versionId = jdbc.queryForObject(
                "SELECT id FROM document_version WHERE document_id = ? AND version_no = 1",
                UUID.class, documentId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE document_version SET status = 'DRAFT' WHERE id = ?", versionId))
                .isInstanceOf(DataAccessException.class);
    }
}
