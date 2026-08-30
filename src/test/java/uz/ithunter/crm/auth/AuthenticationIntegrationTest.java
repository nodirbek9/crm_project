package uz.ithunter.crm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ithunter.crm.AbstractIntegrationTest;
import uz.ithunter.crm.auth.dto.CurrentUserResponse;
import uz.ithunter.crm.auth.dto.LoginRequest;
import uz.ithunter.crm.auth.dto.TokenResponse;
import uz.ithunter.crm.shared.exception.ErrorResponse;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.RoleCode;
import uz.ithunter.crm.user.RoleRepository;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * Phase 3 DoD: S-01, S-02, "login returns a working token", "protected endpoint 401 without a
 * token / 403 with insufficient permission." No admin user-creation endpoint exists until Phase
 * 4, so users are seeded directly via {@link UserRepository}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // No @BeforeEach cleanup: each test below uses its own distinct hardcoded email, so nothing
    // collides within this class. A blanket userRepository.deleteAll() would also now be unsafe in
    // the shared-container test run once any admin mutation has written an audit_log row
    // referencing a user (ON DELETE RESTRICT, spec 20.3) - see AdminUserCrudTest's seeding helper.
    private User seedUser(String email, UserStatus status, RoleCode roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Test " + roleCode);
        user.setStatus(status);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    private String buildExpiredAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("typ", "access")
                .issuedAt(Date.from(Instant.now().minus(Duration.ofHours(2))))
                .expiration(Date.from(Instant.now().minus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String login(String email) {
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, PASSWORD), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    // --- S-01: no token -> 401; expired token -> 401 TOKEN_EXPIRED ---

    @Test
    void meWithoutTokenReturns401Unauthenticated() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/auth/me", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void meWithExpiredTokenReturns401TokenExpired() {
        User user = seedUser("expired@test.local", UserStatus.ACTIVE, RoleCode.OPERATOR);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/auth/me", GET, bearer(buildExpiredAccessToken(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("TOKEN_EXPIRED");
    }

    // --- S-02: a BLOCKED user is rejected at the filter ---

    @Test
    void loginWithBlockedUserReturns403() {
        seedUser("blocked@test.local", UserStatus.BLOCKED, RoleCode.OPERATOR);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest("blocked@test.local", PASSWORD), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_BLOCKED");
    }

    @Test
    void userBlockedAfterTokenIssueIsRejectedOnNextRequest() {
        User user = seedUser("flip@test.local", UserStatus.ACTIVE, RoleCode.OPERATOR);
        String accessToken = login("flip@test.local");

        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/auth/me", GET, bearer(accessToken), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_BLOCKED");
    }

    // --- "login returns a working token" ---

    @Test
    void loginReturnsWorkingTokenUsableOnMe() {
        seedUser("valid@test.local", UserStatus.ACTIVE, RoleCode.OPERATOR);
        String accessToken = login("valid@test.local");
        assertThat(accessToken).isNotBlank();

        ResponseEntity<CurrentUserResponse> response = restTemplate.exchange(
                "/api/auth/me", GET, bearer(accessToken), CurrentUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().email()).isEqualTo("valid@test.local");
        assertThat(response.getBody().roles()).containsExactly("OPERATOR");
    }

    // --- protected endpoint: 401 without a token, 403 with an insufficient permission ---
    // No real business endpoint exists yet (admin CRUD is Phase 4); SecuredTestController below is
    // test-only scaffolding that proves the @PreAuthorize/PermissionAuthorityResolver wiring.

    @Test
    void preAuthorizeEndpointAllowsGrantedPermissionAndRejectsMissingOne() {
        seedUser("admin@test.local", UserStatus.ACTIVE, RoleCode.ADMIN);
        seedUser("operator@test.local", UserStatus.ACTIVE, RoleCode.OPERATOR);

        ResponseEntity<String> allowed = restTemplate.exchange(
                "/api/_test/secured", GET, bearer(login("admin@test.local")), String.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ErrorResponse> denied = restTemplate.exchange(
                "/api/_test/secured", GET, bearer(login("operator@test.local")), ErrorResponse.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().code()).isEqualTo("PERMISSION_DENIED");
    }

    @TestConfiguration
    static class SecuredTestEndpointConfig {
        @Bean
        SecuredTestController securedTestController() {
            return new SecuredTestController();
        }
    }

    @RestController
    static class SecuredTestController {
        @GetMapping("/api/_test/secured")
        @PreAuthorize("hasAuthority('USER_ADMIN:VIEW')")
        public String secured() {
            return "ok";
        }
    }
}
