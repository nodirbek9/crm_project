package uz.ithunter.crm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.applicant.Applicant;
import uz.ithunter.crm.applicant.ApplicantRepository;
import uz.ithunter.crm.applicant.ApplicantType;
import uz.ithunter.crm.application.dto.ApplicationResponse;
import uz.ithunter.crm.application.dto.CreateApplicationRequest;
import uz.ithunter.crm.auth.JwtService;
import uz.ithunter.crm.shared.dto.PageResponse;
import uz.ithunter.crm.user.Permission;
import uz.ithunter.crm.user.PermissionRepository;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RolePermission;
import uz.ithunter.crm.user.RolePermissionRepository;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * API_SPEC.md 3 round trip, plus **I-05** (TEST_MATRIX.md): a PAPER submission is rejected without
 * a resolvable registrar and accepted with one - both at the service layer and directly against
 * {@code ck_application_paper_registrar}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationCrudTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123!";

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
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private uz.ithunter.crm.application.ServiceRepository serviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Applicant applicant;
    private uz.ithunter.crm.application.Service service;

    @BeforeEach
    void setUp() {
        applicant = applicantRepository.save(newApplicant("app-crud-" + java.util.UUID.randomUUID() + "@test.local"));

        service = new uz.ithunter.crm.application.Service();
        service.setCode("SVC_APP_CRUD_" + java.util.UUID.randomUUID().toString().substring(0, 8));
        service.setName("Test Service");
        service.setActive(true);
        service.setContractRequired(true);
        service.setPaymentRequired(true);
        service.setStandaloneLaboratory(false);
        service.setSubmissionChannels(Set.of(SubmissionChannel.PERSONAL_CABINET, SubmissionChannel.PAPER));
        service = serviceRepository.save(service);
    }

    @AfterEach
    void revokeAnyTemporaryGrant() {
        jdbc.update(
                "DELETE FROM role_permission WHERE role_id = (SELECT id FROM role WHERE code = 'OPERATOR') "
                        + "AND permission_id = (SELECT id FROM permission WHERE code = 'APPLICATION:CREATE')");
    }

    private Applicant newApplicant(String email) {
        Applicant a = new Applicant();
        a.setType(ApplicantType.INDIVIDUAL);
        a.setLastName("Karimov");
        a.setFirstName("Aziz");
        a.setBirthDate(java.time.LocalDate.of(1990, 1, 1));
        a.setPassportSeries("AB");
        a.setPassportNumber(String.valueOf(System.nanoTime()).substring(0, 7));
        a.setPinfl(String.valueOf(System.nanoTime()));
        a.setAddress("Tashkent");
        a.setPhone("+998901234567");
        a.setEmail(email);
        return a;
    }

    private User linkedApplicantUser(Applicant applicant) {
        Role role = roleRepository.findByCode(RoleCode.APPLICANT).orElseThrow();
        User user = new User();
        user.setEmail(applicant.getEmail());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName(applicant.getLastName() + " " + applicant.getFirstName());
        user.setApplicantId(applicant.getId());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        return jwtService.generateAccessToken(user);
    }

    private User seedOperator(String email) {
        Role role = roleRepository.findByCode(RoleCode.OPERATOR).orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Test Operator");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    @Test
    void createSubmitGetListRoundTrip() throws Exception {
        User applicantUser = linkedApplicantUser(applicant);
        String token = tokenFor(applicantUser);

        var createRequest = new CreateApplicationRequest(service.getId(), SubmissionChannel.PERSONAL_CABINET,
                null, java.util.Map.of("field1", "value1"), java.util.List.of(java.util.Map.of("name", "item1")));
        var createResult = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        ApplicationResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), ApplicationResponse.class);
        assertThat(created.applicantId()).isEqualTo(applicant.getId());
        assertThat(created.status()).isEqualTo("DRAFT");

        mockMvc.perform(post("/api/applications/" + created.id() + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        var getResult = mockMvc.perform(get("/api/applications/" + created.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ApplicationResponse fetched = objectMapper.readValue(getResult.getResponse().getContentAsString(), ApplicationResponse.class);
        assertThat(fetched.status()).isEqualTo("SUBMITTED");

        var listResult = mockMvc.perform(get("/api/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        PageResponse<?> page = objectMapper.readValue(listResult.getResponse().getContentAsString(), PageResponse.class);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void paperSubmissionByApplicantWithoutRegistrarIsRejected() throws Exception {
        User applicantUser = linkedApplicantUser(applicant);
        String token = tokenFor(applicantUser);

        var request = new CreateApplicationRequest(service.getId(), SubmissionChannel.PAPER, null, null, null);
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void paperSubmissionByStaffWithRegistrarSucceeds() throws Exception {
        grantApplicationCreateToOperator();
        User operator = seedOperator("app-crud-operator-" + java.util.UUID.randomUUID() + "@test.local");
        String token = tokenFor(operator);

        var request = new CreateApplicationRequest(service.getId(), SubmissionChannel.PAPER, applicant.getId(), null, null);
        var result = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        ApplicationResponse created = objectMapper.readValue(result.getResponse().getContentAsString(), ApplicationResponse.class);
        assertThat(created.registeredById()).isEqualTo(operator.getId());
    }

    @Test
    void paperWithoutRegistrarRejectedByDbCheck() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO application (number, applicant_id, service_id, submission_channel, status, form_data) "
                        + "VALUES ('APP-DBTEST-1', ?, ?, 'PAPER', 'DRAFT', '{}'::jsonb)",
                applicant.getId(), service.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_application_paper_registrar");
    }

    @Test
    void staffWithoutApplicationCreateGrantIsRejected() throws Exception {
        User operator = seedOperator("app-crud-noperm-" + java.util.UUID.randomUUID() + "@test.local");
        String token = tokenFor(operator);

        var request = new CreateApplicationRequest(service.getId(), SubmissionChannel.SINGLE_WINDOW, applicant.getId(), null, null);
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherApplicantCannotSeeThisOne() throws Exception {
        User owner = linkedApplicantUser(applicant);
        String ownerToken = tokenFor(owner);
        var createRequest = new CreateApplicationRequest(service.getId(), SubmissionChannel.PERSONAL_CABINET, null, null, null);
        var createResult = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        ApplicationResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), ApplicationResponse.class);

        Applicant other = applicantRepository.save(newApplicant("app-crud-other-" + java.util.UUID.randomUUID() + "@test.local"));
        User otherUser = linkedApplicantUser(other);
        String otherToken = tokenFor(otherUser);

        mockMvc.perform(get("/api/applications/" + created.id()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private void grantApplicationCreateToOperator() {
        Role operator = roleRepository.findByCode(RoleCode.OPERATOR).orElseThrow();
        Permission permission = permissionRepository.findByCode("APPLICATION:CREATE").orElseThrow();
        RolePermission rp = new RolePermission();
        rp.setRole(operator);
        rp.setPermission(permission);
        rolePermissionRepository.save(rp);
    }
}
