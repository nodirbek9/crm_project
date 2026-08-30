package uz.ithunter.crm.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.auth.JwtService;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;
import uz.ithunter.crm.workflow.dto.StageInput;
import uz.ithunter.crm.workflow.dto.TransitionInput;
import uz.ithunter.crm.workflow.dto.UpdateWorkflowStagesRequest;
import uz.ithunter.crm.workflow.dto.WorkflowResponse;

/**
 * Copy-on-write lifecycle: draft copy, stage editing, publish (retires the previous ACTIVE +
 * writes both audit rows), retire. **W-12** (TEST_MATRIX.md): `PATCH` on a published version is
 * rejected. Uses the seeded `WASTE_CERT_ROUTE` v1 (`db/demo/V900`, ACTIVE, 11 stages, 13
 * transitions).
 *
 * <p>{@code @Order} matters here: several tests assume `v1` is still the live `ACTIVE` version
 * (its stage/transition counts, its status), and {@link #publishActivatesDraftAndRetiresPreviousActiveWithAudit}
 * is the one test that actually retires it - it must run last, or every test after it would be
 * asserting against a route this class itself already changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowLifecycleTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ROUTE_CODE = "WASTE_CERT_ROUTE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = userRepository.findByEmail("workflow-admin@test.local").orElseGet(() -> {
            Role role = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
            User user = new User();
            user.setEmail("workflow-admin@test.local");
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setFullName("Workflow Admin");
            user.setStatus(UserStatus.ACTIVE);
            user.setRoles(Set.of(role));
            return userRepository.save(user);
        });
        adminToken = jwtService.generateAccessToken(admin);
    }

    private UUID currentActiveId() {
        return jdbc.queryForObject(
                "SELECT id FROM workflow WHERE code = ? AND status = 'ACTIVE'", UUID.class, ROUTE_CODE);
    }

    private WorkflowResponse createDraft() throws Exception {
        var result = mockMvc.perform(post("/api/workflows/" + ROUTE_CODE + "/versions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowResponse.class);
    }

    private UpdateWorkflowStagesRequest minimalValidGraph() {
        StageInput exec = new StageInput("EXEC_STAGE", "Execution", "EXECUTION", 10, null, true,
                "WORK_IN_PROGRESS", "In progress", null, null, "DEPARTMENT_HEAD_ASSIGNS", 5, 3,
                null, null, true, true, false, null);
        StageInput completion = new StageInput("COMPLETION_STAGE", "Completion", "COMPLETION", 20, null, true,
                "RESULT_ISSUED", "Completed", null, null, "ROUTE_FIXED_USER", null, null,
                null, null, false, false, false, null);
        TransitionInput entry = new TransitionInput(null, "EXEC_STAGE", "ALWAYS", null, 0);
        TransitionInput toCompletion = new TransitionInput("EXEC_STAGE", "COMPLETION_STAGE", "ALWAYS", null, 0);
        return new UpdateWorkflowStagesRequest(List.of(exec, completion), List.of(entry, toCompletion));
    }

    @Test
    @Order(1)
    void createDraftVersionCopiesSeededV1() throws Exception {
        // Compared against whatever is currently ACTIVE (not a hardcoded count): other test
        // classes share this container's database and may have already published a different
        // version of this same route by the time this class runs.
        UUID activeBeforeCopy = currentActiveId();
        Integer activeStageCount = jdbc.queryForObject(
                "SELECT count(*) FROM workflow_stage WHERE workflow_id = ?", Integer.class, activeBeforeCopy);
        Integer activeTransitionCount = jdbc.queryForObject(
                "SELECT count(*) FROM workflow_transition WHERE workflow_id = ?", Integer.class, activeBeforeCopy);

        WorkflowResponse draft = createDraft();
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.code()).isEqualTo(ROUTE_CODE);
        assertThat(draft.version()).isGreaterThan(1);
        assertThat(draft.stages()).hasSize(activeStageCount);
        assertThat(draft.transitions()).hasSize(activeTransitionCount);
    }

    @Test
    @Order(2)
    void updateStagesReplacesGraphOnDraft() throws Exception {
        WorkflowResponse draft = createDraft();

        var result = mockMvc.perform(patch("/api/workflows/" + draft.id() + "/stages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalValidGraph())))
                .andExpect(status().isOk())
                .andReturn();
        WorkflowResponse updated = objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowResponse.class);
        assertThat(updated.stages()).hasSize(2);
        assertThat(updated.transitions()).hasSize(2);
    }

    @Test
    @Order(3)
    void patchOnPublishedVersionIsRejected() throws Exception {
        UUID activeId = currentActiveId();
        mockMvc.perform(patch("/api/workflows/" + activeId + "/stages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalValidGraph())))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    void publishActivatesDraftAndRetiresPreviousActiveWithAudit() throws Exception {
        WorkflowResponse draft = createDraft();
        mockMvc.perform(patch("/api/workflows/" + draft.id() + "/stages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalValidGraph())))
                .andExpect(status().isOk());

        UUID previousActiveId = currentActiveId();

        var result = mockMvc.perform(post("/api/workflows/" + draft.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        WorkflowResponse published = objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowResponse.class);
        assertThat(published.status()).isEqualTo("ACTIVE");

        String previousStatus = jdbc.queryForObject("SELECT status FROM workflow WHERE id = ?", String.class, previousActiveId);
        assertThat(previousStatus).isEqualTo("RETIRED");

        Integer publishedAuditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'WORKFLOW_PUBLISHED' AND entity_id = ? AND case_id IS NULL",
                Integer.class, draft.id());
        assertThat(publishedAuditCount).isEqualTo(1);
        Integer retiredAuditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'WORKFLOW_RETIRED' AND entity_id = ? AND case_id IS NULL",
                Integer.class, previousActiveId);
        assertThat(retiredAuditCount).isEqualTo(1);

        // one and only one ACTIVE row remains for this code
        Integer activeCount = jdbc.queryForObject(
                "SELECT count(*) FROM workflow WHERE code = ? AND status = 'ACTIVE'", Integer.class, ROUTE_CODE);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    @Order(4)
    void retireOnNonActiveVersionIsRejected() throws Exception {
        WorkflowResponse draft = createDraft();
        mockMvc.perform(post("/api/workflows/" + draft.id() + "/retire")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity());
    }
}
