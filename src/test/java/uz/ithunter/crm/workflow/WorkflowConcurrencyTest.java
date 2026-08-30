package uz.ithunter.crm.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * TEST_MATRIX.md C-07: "two simultaneous publish on the same workflow code: one wins on the
 * partial unique index." Pattern per TEST_MATRIX.md F: {@code ExecutorService} + a latch so both
 * requests hit the same window, then assert on final DB state, never on which thread won.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkflowConcurrencyTest extends AbstractIntegrationTest {

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
        User admin = userRepository.findByEmail("workflow-concurrency-admin@test.local").orElseGet(() -> {
            Role role = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
            User user = new User();
            user.setEmail("workflow-concurrency-admin@test.local");
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setFullName("Workflow Concurrency Admin");
            user.setStatus(UserStatus.ACTIVE);
            user.setRoles(Set.of(role));
            return userRepository.save(user);
        });
        adminToken = jwtService.generateAccessToken(admin);
    }

    private String createValidDraft() throws Exception {
        var createResult = mockMvc.perform(post("/api/workflows/" + ROUTE_CODE + "/versions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        WorkflowResponse draft = objectMapper.readValue(createResult.getResponse().getContentAsString(), WorkflowResponse.class);

        StageInput execStage = new StageInput("EXEC_STAGE", "Execution", "EXECUTION", 10, null, true,
                "WORK_IN_PROGRESS", "In progress", null, null, "DEPARTMENT_HEAD_ASSIGNS", null, null,
                null, null, true, true, false, null);
        StageInput completionStage = new StageInput("COMPLETION_STAGE", "Completion", "COMPLETION", 20, null, true,
                "RESULT_ISSUED", "Completed", null, null, "ROUTE_FIXED_USER", null, null,
                null, null, false, false, false, null);
        var stages = List.of(execStage, completionStage);
        var transitions = List.of(
                new TransitionInput(null, "EXEC_STAGE", "ALWAYS", null, 0),
                new TransitionInput("EXEC_STAGE", "COMPLETION_STAGE", "ALWAYS", null, 0));

        mockMvc.perform(patch("/api/workflows/" + draft.id() + "/stages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateWorkflowStagesRequest(stages, transitions))))
                .andExpect(status().isOk());

        return draft.id().toString();
    }

    private Callable<Integer> publishTask(String draftId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/workflows/" + draftId + "/publish")
                            .header("Authorization", "Bearer " + adminToken))
                    .andReturn().getResponse().getStatus();
        };
    }

    /**
     * {@code activeCount == 1} is the DoD invariant and must hold after EVERY attempt, race or no
     * race: even two publishes that happen not to overlap at the SQL level are still each a correct
     * retire-then-activate transaction, and the second one retires the first's newly-active row.
     *
     * <p>{@code successCount == 1} is the stronger, timing-dependent claim that the two publishes
     * actually collided at {@code uq_workflow_one_active} (one request's UPDATE raced the other's and
     * lost). Whether the two {@code ExecutorService} threads truly overlap at the database statement
     * level - as opposed to merely being submitted "at the same time" - depends on OS thread
     * scheduling, which a full {@code mvn verify} run (87 test classes, shared connection pool, JIT
     * already warm) can make fast enough that one publish's transaction commits before the other's
     * even reaches the UPDATE. Retrying with a fresh draft pair keeps the test asserting the DB
     * constraint actually arbitrates a real collision (C-07's point), without being flaky about
     * exactly when the OS schedules two threads.
     */
    @Test
    void twoSimultaneousPublishesOnlyOneWins() throws Exception {
        boolean sawGenuineCollision = false;
        for (int attempt = 1; attempt <= 20 && !sawGenuineCollision; attempt++) {
            String draftAId = createValidDraft();
            String draftBId = createValidDraft();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Integer> resultA = executor.submit(publishTask(draftAId, ready, start));
            Future<Integer> resultB = executor.submit(publishTask(draftBId, ready, start));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            int statusA = resultA.get(10, TimeUnit.SECONDS);
            int statusB = resultB.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            long successCount = List.of(statusA, statusB).stream().filter(s -> s == 200).count();
            sawGenuineCollision = successCount == 1;

            // The invariant that must NEVER break, race or no race.
            Integer activeCount = jdbc.queryForObject(
                    "SELECT count(*) FROM workflow WHERE code = ? AND status = 'ACTIVE'",
                    Integer.class, ROUTE_CODE);
            assertThat(activeCount).isEqualTo(1);
        }
        assertThat(sawGenuineCollision)
                .as("uq_workflow_one_active should reject at least one of 20 racing publish attempts")
                .isTrue();
    }
}
