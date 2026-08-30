package uz.ithunter.crm.applicant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.applicant.dto.CreateApplicantRequest;
import uz.ithunter.crm.auth.dto.LoginRequest;
import uz.ithunter.crm.auth.dto.TokenResponse;
import uz.ithunter.crm.user.UserRepository;

/**
 * TEST_MATRIX.md I-03/I-04: INDIVIDUAL/LEGAL_ENTITY mandatory-field validation, asserted both at
 * the REST layer (Bean Validation groups) and directly against the database
 * (ck_applicant_individual/ck_applicant_legal), per the DoD's explicit wording.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicantValidationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    private CreateApplicantRequest validIndividual(String email) {
        return new CreateApplicantRequest(
                ApplicantType.INDIVIDUAL,
                "Karimov", "Aziz", null, LocalDate.of(1990, 5, 1), "AB", "1234567", "12345671234567",
                null, null, null, null, null,
                "Tashkent", "+998901234567", email, "Password123!");
    }

    private CreateApplicantRequest validLegalEntity(String email) {
        return new CreateApplicantRequest(
                ApplicantType.LEGAL_ENTITY,
                null, null, null, null, null, null, null,
                "Eco LLC", "123456789", "Karimov Aziz", "Director", null,
                "Tashkent", "+998901234567", email, "Password123!");
    }

    // --- I-03: INDIVIDUAL missing pinfl ---

    @Test
    void individualMissingPinflRejectedByRestLayer() throws Exception {
        var request = new CreateApplicantRequest(
                ApplicantType.INDIVIDUAL,
                "Karimov", "Aziz", null, LocalDate.of(1990, 5, 1), "AB", "1234567", null,
                null, null, null, null, null,
                "Tashkent", "+998901234567", "i03-rest@test.local", "Password123!");

        mockMvc.perform(post("/api/applicants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void individualMissingPinflRejectedByDbCheck() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO applicant (type, last_name, first_name, birth_date, passport_series, "
                        + "passport_number, address, phone, email) "
                        + "VALUES ('INDIVIDUAL', 'X', 'Y', '1990-01-01', 'AB', '123', 'addr', 'phone', 'i03-db@test.local')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_applicant_individual");
    }

    // --- I-04: LEGAL_ENTITY missing tin; individual-only field on a legal entity ---

    @Test
    void legalEntityMissingTinRejectedByRestLayer() throws Exception {
        var request = new CreateApplicantRequest(
                ApplicantType.LEGAL_ENTITY,
                null, null, null, null, null, null, null,
                "Eco LLC", null, "Karimov Aziz", "Director", null,
                "Tashkent", "+998901234567", "i04-rest@test.local", "Password123!");

        mockMvc.perform(post("/api/applicants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void individualOnlyFieldOnLegalEntityRejectedByRestLayer() throws Exception {
        var request = new CreateApplicantRequest(
                ApplicantType.LEGAL_ENTITY,
                "Karimov", null, null, null, null, null, null,
                "Eco LLC", "123456789", "Karimov Aziz", "Director", null,
                "Tashkent", "+998901234567", "i04-wrongtype@test.local", "Password123!");

        mockMvc.perform(post("/api/applicants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legalEntityMissingTinRejectedByDbCheck() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO applicant (type, org_name, representative_full_name, representative_position, "
                        + "address, phone, email) "
                        + "VALUES ('LEGAL_ENTITY', 'Org', 'Rep', 'Pos', 'addr', 'phone', 'i04-db@test.local')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_applicant_legal");
    }

    // --- happy path: self-registration side effect ---

    @Test
    void individualRegistrationCreatesLoginableApplicantUser() throws Exception {
        String email = "i03-happy@test.local";
        mockMvc.perform(post("/api/applicants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validIndividual(email))))
                .andExpect(status().isCreated());

        var user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getApplicantId()).isNotNull();
        assertThat(user.getRoles()).extracting(r -> r.getCode().name()).containsExactly("APPLICANT");

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponse token = objectMapper.readValue(loginResult.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(token.accessToken()).isNotBlank();
    }

    @Test
    void legalEntityRegistrationSucceeds() throws Exception {
        var result = mockMvc.perform(post("/api/applicants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLegalEntity("i04-happy@test.local"))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"orgName\":\"Eco LLC\"");
    }

    @Test
    void applicantEndpointWithoutTokenIsUnauthorizedForGet() throws Exception {
        mockMvc.perform(get("/api/applicants/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }
}
