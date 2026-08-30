package uz.ithunter.crm.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.User;

/**
 * HS256 access/refresh token issuing and parsing (SECURITY_SPEC.md 1). Permissions are
 * deliberately NOT a claim - {@link PermissionAuthorityResolver} resolves them fresh from
 * {@code role_permission} on every request, so a revoked grant takes effect immediately instead
 * of only after the token expires.
 */
@Service
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
            @Value("${jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    public String generateAccessToken(User user) {
        return generateToken(user, accessTokenTtl, TYPE_ACCESS);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, refreshTokenTtl, TYPE_REFRESH);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    private String generateToken(User user, Duration ttl, String type) {
        Instant now = Instant.now();
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).map(Enum::name).toList();

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roleCodes)
                .claim(CLAIM_TYPE, type)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));

        if (user.getDepartmentId() != null) {
            builder.claim("departmentId", user.getDepartmentId().toString());
        }
        if (user.getApplicantId() != null) {
            builder.claim("applicantId", user.getApplicantId().toString());
        }

        return builder.signWith(key).compact();
    }

    /** Throws {@code ExpiredJwtException} or another {@code JwtException} subtype on any invalid token. */
    public Claims parseOrThrow(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }
}
