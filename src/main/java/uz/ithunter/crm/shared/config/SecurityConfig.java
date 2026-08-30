package uz.ithunter.crm.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uz.ithunter.crm.auth.AppUserDetailsService;
import uz.ithunter.crm.auth.JwtAuthenticationFilter;
import uz.ithunter.crm.shared.idempotency.IdempotencyFilter;
import uz.ithunter.crm.shared.security.CustomAccessDeniedHandler;
import uz.ithunter.crm.shared.security.CustomAuthenticationEntryPoint;

/**
 * Phase 3 security configuration (SECURITY_SPEC.md 1), replacing the Phase 1 placeholder wholesale
 * as that class's own Javadoc anticipated. Stateless JWT bearer auth: {@link JwtAuthenticationFilter}
 * sets the {@code Authentication} (and re-checks live account status) before
 * {@link UsernamePasswordAuthenticationFilter} would normally run; permission checks happen via
 * {@code @PreAuthorize("hasAuthority(...)")} on controller methods, backed by
 * {@code PermissionAuthorityResolver}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    // Only login/refresh are public per API_SPEC.md 1 - /auth/logout and /auth/me require
    // authentication, so they must NOT be matched here.
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
            IdempotencyFilter idempotencyFilter, CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Applicant self-registration (ASSUMPTIONS.md A17) - POST only; GET/PATCH
                        // on /api/applicants/{id} stay authenticated.
                        .requestMatchers(HttpMethod.POST, "/api/applicants").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Must run AFTER JwtAuthenticationFilter: it attributes command_log rows to the
                // authenticated principal (Phase 12, C-05).
                .addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // spec-mandated BCrypt strength 10 (SECURITY_SPEC.md 1).
        return new BCryptPasswordEncoder(10);
    }

    // FINAL_IMPLEMENTATION_ORDER.md Phase 3's "UserDetails adapter" - used only for the login-time
    // credential + account-status check (AuthenticationService.login), never for per-request
    // authorization, which JwtAuthenticationFilter/PermissionAuthorityResolver handle separately.
    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
