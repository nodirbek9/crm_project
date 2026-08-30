package uz.ithunter.crm.casemodule;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.applicant.ApplicantRepository;
import uz.ithunter.crm.applicant.ApplicantType;
import uz.ithunter.crm.application.ApplicationRepository;
import uz.ithunter.crm.application.SubmissionChannel;
import uz.ithunter.crm.application.dto.ApplicationResponse;
import uz.ithunter.crm.application.dto.CreateApplicationRequest;
import uz.ithunter.crm.audit.AuditLogRepository;
import uz.ithunter.crm.auth.JwtService;
import uz.ithunter.crm.user.Department;
import uz.ithunter.crm.user.DepartmentRepository;
import uz.ithunter.crm.user.PermissionRepository;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;
import uz.ithunter.crm.workflow.ConditionType;
import uz.ithunter.crm.workflow.ExternalStageRepository;
import uz.ithunter.crm.workflow.StageType;
import uz.ithunter.crm.workflow.Workflow;
import uz.ithunter.crm.workflow.WorkflowRepository;
import uz.ithunter.crm.workflow.WorkflowStage;
import uz.ithunter.crm.workflow.WorkflowStageRepository;
import uz.ithunter.crm.workflow.WorkflowStatus;
import uz.ithunter.crm.workflow.WorkflowTransition;
import uz.ithunter.crm.workflow.WorkflowTransitionRepository;

/**
 * Shared fixture for the Phase 7 integration tests (TEST_MATRIX.md I-01, I-02, I-06 … I-09, W-01,
 * W-03, S-05, S-07).
 *
 * <p>Every test seeds its OWN department, service, route, users and applicant. That is not
 * over-engineering: {@link AbstractIntegrationTest} shares one PostgreSQL container across the whole
 * {@code mvn verify} run, so anything read from the demo seed would couple these assertions to rows
 * another phase's test may already have moved.
 *
 * <p>{@code APPLICATION:EDIT} and {@code CASE:EDIT} are granted temporarily and revoked in
 * {@link #revokeTemporaryGrants()}: the seeded matrix gives them to no role at all
 * (ASSUMPTIONS.md A30), and {@code SecurityGrantMatrixTest} asserts that matrix exactly, so a leaked
 * grant would fail a different test class.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractCaseIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionRepository permissionRepository;

    @Autowired
    protected ApplicantRepository applicantRepository;

    @Autowired
    protected DepartmentRepository departmentRepository;

    @Autowired
    protected uz.ithunter.crm.application.ServiceRepository serviceRepository;

    @Autowired
    protected ApplicationRepository applicationRepository;

    @Autowired
    protected WorkflowRepository workflowRepository;

    @Autowired
    protected WorkflowStageRepository workflowStageRepository;

    @Autowired
    protected WorkflowTransitionRepository workflowTransitionRepository;

    @Autowired
    protected ExternalStageRepository externalStageRepository;

    @Autowired
    protected ElectronicCaseRepository electronicCaseRepository;

    @Autowired
    protected CaseStageRepository caseStageRepository;

    @Autowired
    protected CaseItemRepository caseItemRepository;

    @Autowired
    protected PrimaryCheckRepository primaryCheckRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    /** Revokes every grant a test added, so the seeded matrix is intact for the next class. */
    @AfterEach
    void revokeTemporaryGrants() {
        for (String[] grant : List.copyOf(temporaryGrants)) {
            jdbc.update("DELETE FROM role_permission WHERE role_id = (SELECT id FROM role WHERE code = ?) "
                    + "AND permission_id = (SELECT id FROM permission WHERE code = ?)", grant[0], grant[1]);
        }
        temporaryGrants.clear();
    }

    private final List<String[]> temporaryGrants = new java.util.ArrayList<>();

    /**
     * Grants a permission for the duration of one test, reverted in {@link #revokeTemporaryGrants()}.
     *
     * <p>Only tracks it for revocation when the INSERT actually added a row. Calling this on a
     * permission the seed already grants (e.g. {@code APPLICANT}/{@code APPLICATION:CREATE}) must
     * be a harmless no-op, not a ticking time bomb that DELETEs a real seeded grant out from under
     * every other test class sharing this container for the rest of the {@code mvn verify} run.
     */
    protected void grant(RoleCode role, String permissionCode) {
        int inserted = jdbc.update("INSERT INTO role_permission (role_id, permission_id) "
                + "SELECT r.id, p.id FROM role r, permission p WHERE r.code = ? AND p.code = ? "
                + "AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id "
                + "AND rp.permission_id = p.id)", role.name(), permissionCode);
        if (inserted > 0) {
            temporaryGrants.add(new String[] {role.name(), permissionCode});
        }
    }

    protected String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected Department seedDepartment(String prefix) {
        Department department = new Department();
        department.setCode((prefix + "_" + unique()).toUpperCase());
        department.setName("Test department " + prefix);
        department.setActive(true);
        return departmentRepository.save(department);
    }

    protected uz.ithunter.crm.application.Service seedService() {
        uz.ithunter.crm.application.Service service = new uz.ithunter.crm.application.Service();
        service.setCode("SVC_CASE_" + unique().toUpperCase());
        service.setName("Phase 7 test service");
        service.setActive(true);
        service.setContractRequired(true);
        service.setPaymentRequired(true);
        service.setStandaloneLaboratory(false);
        service.setSubmissionChannels(Set.of(SubmissionChannel.PERSONAL_CABINET, SubmissionChannel.PAPER));
        return serviceRepository.save(service);
    }

    protected Applicant seedApplicant() {
        Applicant applicant = new Applicant();
        applicant.setType(ApplicantType.INDIVIDUAL);
        applicant.setLastName("Karimov");
        applicant.setFirstName("Aziz");
        applicant.setBirthDate(LocalDate.of(1990, 1, 1));
        applicant.setPassportSeries("AB");
        applicant.setPassportNumber(String.valueOf(System.nanoTime()).substring(0, 7));
        applicant.setPinfl(String.valueOf(System.nanoTime()));
        applicant.setAddress("Tashkent");
        applicant.setPhone("+998901234567");
        applicant.setEmail("case-test-" + unique() + "@test.local");
        return applicantRepository.save(applicant);
    }

    protected User seedApplicantUser(Applicant applicant) {
        User user = new User();
        user.setEmail(applicant.getEmail());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(applicant.getLastName() + " " + applicant.getFirstName());
        user.setApplicantId(applicant.getId());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleRepository.findByCode(RoleCode.APPLICANT).orElseThrow()));
        return userRepository.save(user);
    }

    protected User seedStaffUser(RoleCode role, UUID departmentId) {
        User user = new User();
        user.setEmail("case-staff-" + unique() + "@test.local");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Test " + role.name());
        user.setDepartmentId(departmentId);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleRepository.findByCode(role).orElseThrow()));
        return userRepository.save(user);
    }

    protected String token(User user) {
        return jwtService.generateAccessToken(user);
    }

    protected Workflow seedWorkflow(UUID serviceId, UUID mainDepartmentId, Integer totalDeadlineDays) {
        Workflow workflow = new Workflow();
        workflow.setServiceId(serviceId);
        workflow.setCode("ROUTE_" + unique().toUpperCase());
        workflow.setVersion(1);
        workflow.setName("Phase 7 test route");
        workflow.setStatus(WorkflowStatus.ACTIVE);
        workflow.setMainResponsibleDepartmentId(mainDepartmentId);
        workflow.setExpeditedAllowed(true);
        workflow.setContractRequired(false);
        workflow.setPaymentRequired(false);
        workflow.setTotalDeadlineDays(totalDeadlineDays);
        // ck_workflow_published (V4): any non-DRAFT status requires published_at to be set.
        workflow.setPublishedAt(java.time.Instant.now());
        return workflowRepository.save(workflow);
    }

    protected WorkflowStage seedStage(Workflow workflow, String code, StageType type, int sequence,
            String externalStageCode, UUID responsibleDepartmentId, String parallelGroup, boolean required) {
        WorkflowStage stage = new WorkflowStage();
        stage.setWorkflowId(workflow.getId());
        stage.setCode(code);
        stage.setName(code + " stage");
        stage.setStageType(type);
        stage.setSequence(sequence);
        stage.setParallelGroup(parallelGroup);
        stage.setRequired(required);
        stage.setExternalStageId(externalStageRepository.findByCode(externalStageCode).orElseThrow().getId());
        stage.setInternalStatusLabel(code + " in progress");
        stage.setResponsibleDepartmentId(responsibleDepartmentId);
        stage.setDeadlineDays(5);
        stage.setRequiresResult(false);
        return workflowStageRepository.save(stage);
    }

    protected WorkflowTransition seedTransition(Workflow workflow, UUID fromStageId, UUID toStageId,
            ConditionType conditionType, String conditionValue, int sequence) {
        WorkflowTransition transition = new WorkflowTransition();
        transition.setWorkflowId(workflow.getId());
        transition.setFromStageId(fromStageId);
        transition.setToStageId(toStageId);
        transition.setConditionType(conditionType);
        transition.setConditionValue(conditionValue);
        transition.setSequence(sequence);
        return workflowTransitionRepository.save(transition);
    }

    /**
     * Creates and submits an application through the real API as the applicant - the only path that
     * produces a SUBMITTED row with the {@code formData.items} shape Phase 5 actually writes.
     */
    protected UUID submittedApplication(User applicantUser, UUID serviceId,
            Map<String, Object> formData, List<Map<String, Object>> items) throws Exception {
        String applicantToken = token(applicantUser);
        CreateApplicationRequest request = new CreateApplicationRequest(serviceId,
                SubmissionChannel.PERSONAL_CABINET, null, formData, items);
        String created = mockMvc.perform(MockMvcRequestBuilders.post("/api/applications")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        ApplicationResponse response = objectMapper.readValue(created, ApplicationResponse.class);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/applications/" + response.id() + "/submit")
                        .header("Authorization", "Bearer " + applicantToken))
                .andReturn();
        return response.id();
    }

    /** The default single-item payload: enough data for {@code REQUIRED_DATA_MISSING} not to trigger. */
    protected List<Map<String, Object>> oneItem() {
        return List.of(Map.of("name", "Waste sample", "quantity", 2, "unit", "PCS",
                "objectAddress", "Tashkent, Chilonzor"));
    }
}
