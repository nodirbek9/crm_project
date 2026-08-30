package uz.ithunter.crm.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import uz.ithunter.crm.admin.dto.CreateExternalStageRequest;
import uz.ithunter.crm.admin.dto.CreateServiceRequest;
import uz.ithunter.crm.admin.dto.CreateWorkTypeRequest;
import uz.ithunter.crm.admin.dto.ExternalStageResponse;
import uz.ithunter.crm.admin.dto.ServiceResponse;
import uz.ithunter.crm.admin.dto.UpdateExternalStageRequest;
import uz.ithunter.crm.admin.dto.UpdateServiceRequest;
import uz.ithunter.crm.admin.dto.UpdateWorkTypeRequest;
import uz.ithunter.crm.admin.dto.WorkTypeResponse;
import uz.ithunter.crm.auth.JwtService;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * Phase 4 DoD: "admin CRUD round-trips through MockMvc" for the {@code REFERENCE_DATA}-gated
 * resources (services, work types, external stages) - structurally identical CRUD, one test class.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReferenceDataCrudTest extends AbstractIntegrationTest {

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
        adminToken = jwtService.generateAccessToken(getOrCreateUser("admin-refdata@test.local", RoleCode.ADMIN));
        operatorToken = jwtService.generateAccessToken(getOrCreateUser("operator-refdata@test.local", RoleCode.OPERATOR));
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
    void serviceCreateListUpdateRoundTrip() throws Exception {
        var createRequest = new CreateServiceRequest("SVC_TEST", "Test Service", "desc", true, true, false,
                Set.of("PERSONAL_CABINET", "SINGLE_WINDOW"));
        var createResult = mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        ServiceResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), ServiceResponse.class);
        assertThat(created.code()).isEqualTo("SVC_TEST");
        assertThat(created.submissionChannels()).containsExactlyInAnyOrder("PERSONAL_CABINET", "SINGLE_WINDOW");

        mockMvc.perform(get("/api/admin/services").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        var updateRequest = new UpdateServiceRequest("Renamed Service", "desc2", true, true, true, true,
                Set.of("PAPER"));
        var updateResult = mockMvc.perform(patch("/api/admin/services/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        ServiceResponse updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(), ServiceResponse.class);
        assertThat(updated.name()).isEqualTo("Renamed Service");
        assertThat(updated.standaloneLaboratory()).isTrue();
    }

    @Test
    void workTypeCreateListUpdateRoundTrip() throws Exception {
        var createRequest = new CreateWorkTypeRequest("WT_TEST", "Test Work Type", "SCOPE", "STAGE", false, "basis doc");
        var createResult = mockMvc.perform(post("/api/admin/work-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        WorkTypeResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), WorkTypeResponse.class);
        assertThat(created.code()).isEqualTo("WT_TEST");

        var updateRequest = new UpdateWorkTypeRequest("Renamed Work Type", "SCOPE2", "STAGE2", true, "basis doc 2", true);
        var updateResult = mockMvc.perform(patch("/api/admin/work-types/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        WorkTypeResponse updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(), WorkTypeResponse.class);
        assertThat(updated.name()).isEqualTo("Renamed Work Type");
        assertThat(updated.requiresContractAmountBracket()).isTrue();
    }

    @Test
    void externalStageCreateListUpdateRoundTrip() throws Exception {
        var createRequest = new CreateExternalStageRequest("EXT_TEST", "Test Stage", 5);
        var createResult = mockMvc.perform(post("/api/admin/external-stages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        ExternalStageResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), ExternalStageResponse.class);
        assertThat(created.code()).isEqualTo("EXT_TEST");

        var updateRequest = new UpdateExternalStageRequest("Renamed Stage", 15, true);
        var updateResult = mockMvc.perform(patch("/api/admin/external-stages/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();
        ExternalStageResponse updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(), ExternalStageResponse.class);
        assertThat(updated.nameForApplicant()).isEqualTo("Renamed Stage");
        assertThat(updated.sequence()).isEqualTo(15);
    }

    @Test
    void referenceDataMutationsRejectedForOperator() throws Exception {
        var request = new CreateServiceRequest("SVC_DENY", "X", null, true, true, false, Set.of("PAPER"));
        mockMvc.perform(post("/api/admin/services")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
