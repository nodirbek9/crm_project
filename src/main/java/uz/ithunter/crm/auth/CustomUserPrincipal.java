package uz.ithunter.crm.auth;

import java.util.UUID;

/** The {@code Authentication} principal set by {@link JwtAuthenticationFilter}, retrievable via {@code @AuthenticationPrincipal}. */
public record CustomUserPrincipal(UUID userId, String email, UUID departmentId, UUID applicantId) {
}
