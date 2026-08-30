package uz.ithunter.crm.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.admin.dto.CreateDepartmentRequest;
import uz.ithunter.crm.admin.dto.CreatePositionRequest;
import uz.ithunter.crm.admin.dto.CreateUserRequest;
import uz.ithunter.crm.admin.dto.DepartmentResponse;
import uz.ithunter.crm.admin.dto.PositionResponse;
import uz.ithunter.crm.admin.dto.UpdateDepartmentRequest;
import uz.ithunter.crm.admin.dto.UpdatePositionRequest;
import uz.ithunter.crm.admin.dto.UpdateUserRequest;
import uz.ithunter.crm.admin.dto.UserResponse;
import uz.ithunter.crm.auth.JwtService;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * Phase 4 DoD: "admin CRUD round-trips through MockMvc" for the {@code USER_ADMIN}-gated
 * resources (users, departments, positions).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserCrudTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String operatorToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateAccessToken(getOrCreateUser("admin-crud@test.local", RoleCode.ADMIN));
        operatorToken = jwtService.generateAccessToken(getOrCreateUser("operator-crud@test.local", RoleCode.OPERATOR));
    }

    /**
     * Find-or-create rather than delete-and-recreate: once a test writes an admin mutation, the
     * acting user has {@code audit_log} rows pointing at it via {@code ON DELETE RESTRICT}
     * (V10, spec 20.3's audit trail must survive), so a blanket {@code deleteAll()} between tests
     * would fail after the first mutation in the whole shared-container test run.
     */
    private User getOrCreateUser(String email, RoleCode roleCode) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setFullName("Test " + roleCode);
            user.setStatus(UserStatus.ACTIVE);
            user.setRoles(Set.of(role));
            return userRepository.save(user);
        });
    }

    @Test
    void userCreateListUpdateBlockRoundTrip() throws Exception {
        var createRequest = new CreateUserRequest(
                "newstaff@test.local", PASSWORD, "New Staff", null, null, Set.of("OPERATOR"));

        var createResult = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        UserResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), UserResponse.class);
        assertThat(created.email()).isEqualTo("newstaff@test.local");
        assertThat(created.roles()).containsExactly("OPERATOR");

        // A large explicit size: the default page size is smaller than the demo seed's own user
        // count (Phase 13's V900 additions) plus everything earlier tests in this shared container
        // have created, so the default first page alone is not guaranteed to include this row.
        var listResult = mockMvc.perform(get("/api/admin/users").param("size", "200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        PageResponse<UserResponse> page = objectMapper.readValue(
                listResult.getResponse().getContentAsString(), objectMapper.constructType(
                        objectMapper.getTypeFactory().constructParametricType(PageResponse.class, UserResponse.class)));
        assertThat(page.content()).extracting(UserResponse::email).contains("newstaff@test.local");

        var updateRequest = new UpdateUserRequest("Updated Name", null, null, Set.of("OPERATOR"), created.version());
        var updateResult = mockMvc.perform(patch("/api/admin/users/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        UserResponse updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(), UserResponse.class);
        assertThat(updated.fullName()).isEqualTo("Updated Name");

        var blockResult = mockMvc.perform(post("/api/admin/users/" + created.id() + "/block")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        UserResponse blocked = objectMapper.readValue(blockResult.getResponse().getContentAsString(), UserResponse.class);
        assertThat(blocked.status()).isEqualTo("BLOCKED");
    }

    // ---- TEST_MATRIX.md C-04: a stale PATCH (old version) -> 409 CONCURRENT_MODIFICATION ----

    @Test
    void aStalePatchWithAnOutdatedVersionIsRejected() throws Exception {
        var createRequest = new CreateUserRequest(
                "stale-version@test.local", PASSWORD, "Original Name", null, null, Set.of("OPERATOR"));
        var createResult = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        UserResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), UserResponse.class);

        // First PATCH with the current version succeeds and bumps the row to version+1.
        var firstUpdate = new UpdateUserRequest("First Update", null, null, Set.of("OPERATOR"), created.version());
        mockMvc.perform(patch("/api/admin/users/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstUpdate)))
                .andExpect(status().isOk());

        // A second PATCH replaying the SAME now-stale version must be rejected, not silently applied.
        var stalePatch = new UpdateUserRequest("Stale Update", null, null, Set.of("OPERATOR"), created.version());
        mockMvc.perform(patch("/api/admin/users/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stalePatch)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void creatingUserWithApplicantRoleIsRejected() throws Exception {
        var request = new CreateUserRequest("applicant-attempt@test.local", PASSWORD, "X", null, null, Set.of("APPLICANT"));
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userMutationsRejectedForOperator() throws Exception {
        var request = new CreateUserRequest("x@test.local", PASSWORD, "X", null, null, Set.of("OPERATOR"));
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void departmentCreateListUpdateRoundTrip() throws Exception {
        var createRequest = new CreateDepartmentRequest("DEPT_TEST", "Test Department", null, null);
        var createResult = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        DepartmentResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), DepartmentResponse.class);
        assertThat(created.code()).isEqualTo("DEPT_TEST");

        mockMvc.perform(get("/api/admin/departments").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        var updateRequest = new UpdateDepartmentRequest("Renamed Department", null, null, true);
        var updateResult = mockMvc.perform(patch("/api/admin/departments/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        DepartmentResponse updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(), DepartmentResponse.class);
        assertThat(updated.name()).isEqualTo("Renamed Department");
    }

    @Test
    void positionCreateListUpdateRoundTrip() throws Exception {
        var createRequest = new CreatePositionRequest("POS_TEST", "Test Position");
        var createResult = mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        PositionResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), PositionResponse.class);
        assertThat(created.code()).isEqualTo("POS_TEST");

        var updateRequest = new UpdatePositionRequest("Renamed Position", true);
        var updateResult = mockMvc.perform(patch("/api/admin/positions/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        PositionResponse updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(), PositionResponse.class);
        assertThat(updated.name()).isEqualTo("Renamed Position");
    }
}
