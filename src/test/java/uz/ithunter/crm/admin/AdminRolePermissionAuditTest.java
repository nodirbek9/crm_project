package uz.ithunter.crm.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.admin.dto.RolePermissionsResponse;
import uz.ithunter.crm.admin.dto.UpdateRolePermissionsRequest;
import uz.ithunter.crm.auth.JwtService;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * TEST_MATRIX.md A-04: "an administrative event (role permission change) is stored with
 * {@code case_id IS NULL} and old/new values." This is the direct, end-to-end test of that DoD
 * line - through the real admin endpoint, not by inserting an {@code AuditEvent} by hand.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRolePermissionAuditTest extends AbstractIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String operatorToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateAccessToken(getOrCreateUser("admin-rolep@test.local", RoleCode.ADMIN));
        operatorToken = jwtService.generateAccessToken(getOrCreateUser("operator-rolep@test.local", RoleCode.OPERATOR));
    }

    /** Find-or-create - see AdminUserCrudTest's javadoc on the same helper for why. */
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
    void getReturnsSeededOperatorGrants() throws Exception {
        var result = mockMvc.perform(get("/api/admin/roles/OPERATOR/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        RolePermissionsResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), RolePermissionsResponse.class);
        assertThat(response.permissionCodes()).contains("CASE:VIEW", "TASK:VIEW", "APPLICATION:VIEW", "REPORTING:VIEW");
    }

    @Test
    void patchChangesGrantsAndWritesAdministrativeAuditRow() throws Exception {
        var request = new UpdateRolePermissionsRequest(Set.of(
                "CASE:VIEW", "TASK:VIEW", "APPLICATION:VIEW", "REPORTING:VIEW", "AUDIT:VIEW"));

        mockMvc.perform(patch("/api/admin/roles/OPERATOR/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // A-04: the newest ROLE_PERMISSION_CHANGED row has case_id IS NULL and old/new values present.
        var row = jdbc.queryForMap(
                "SELECT case_id, old_value, new_value FROM audit_log "
                        + "WHERE action = 'ROLE_PERMISSION_CHANGED' ORDER BY seq DESC LIMIT 1");
        assertThat(row.get("case_id")).isNull();
        assertThat(row.get("old_value")).isNotNull();
        assertThat(row.get("new_value")).isNotNull();
        assertThat(row.get("new_value").toString()).contains("AUDIT:VIEW");

        // revert so later tests / other classes see the seeded matrix unchanged
        var revert = new UpdateRolePermissionsRequest(Set.of(
                "CASE:VIEW", "TASK:VIEW", "APPLICATION:VIEW", "REPORTING:VIEW"));
        mockMvc.perform(patch("/api/admin/roles/OPERATOR/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revert)))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPermissionCodeIsRejected() throws Exception {
        var request = new UpdateRolePermissionsRequest(Set.of("NOT_A_REAL_CODE"));
        mockMvc.perform(patch("/api/admin/roles/OPERATOR/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolePermissionMutationRejectedForOperator() throws Exception {
        var request = new UpdateRolePermissionsRequest(Set.of("CASE:VIEW"));
        mockMvc.perform(patch("/api/admin/roles/OPERATOR/permissions")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
