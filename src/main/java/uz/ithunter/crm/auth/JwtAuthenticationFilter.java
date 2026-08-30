package uz.ithunter.crm.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.ithunter.crm.shared.exception.ErrorResponseWriter;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * Validates the bearer token, then re-loads the {@link User} row fresh from the DB by id on every
 * request. That reload is not optional: it is what lets this filter enforce spec 16.3's
 * "{@code status = BLOCKED|DISABLED} -> 403 at the filter, before any business code" even for a
 * token that was perfectly valid at issue time - a user blocked mid-session is rejected on their
 * very next request, not only after their token happens to expire. Permissions are resolved from
 * that same fresh row via {@link PermissionAuthorityResolver}, never from JWT claims.
 *
 * <p>On missing/expired/malformed tokens this filter does NOT reject the request itself - it
 * records the reason on a request attribute and lets the chain continue with no
 * {@code Authentication} set, so {@code CustomAuthenticationEntryPoint} (which runs later, only
 * if the endpoint actually required authentication) can pick 401 UNAUTHENTICATED vs
 * 401 TOKEN_EXPIRED and public endpoints stay reachable without a token at all.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_ERROR_ATTRIBUTE = "jwt.error";
    public static final String ERROR_TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String ERROR_INVALID_TOKEN = "INVALID_TOKEN";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PermissionAuthorityResolver permissionAuthorityResolver;
    private final ErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
            PermissionAuthorityResolver permissionAuthorityResolver, ErrorResponseWriter errorResponseWriter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.permissionAuthorityResolver = permissionAuthorityResolver;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length());
        Claims claims;
        try {
            claims = jwtService.parseOrThrow(token);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(JWT_ERROR_ATTRIBUTE, ERROR_TOKEN_EXPIRED);
            chain.doFilter(request, response);
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            request.setAttribute(JWT_ERROR_ATTRIBUTE, ERROR_INVALID_TOKEN);
            chain.doFilter(request, response);
            return;
        }

        if (!JwtService.TYPE_ACCESS.equals(jwtService.extractType(claims))) {
            request.setAttribute(JWT_ERROR_ATTRIBUTE, ERROR_INVALID_TOKEN);
            chain.doFilter(request, response);
            return;
        }

        UUID userId = jwtService.extractUserId(claims);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            request.setAttribute(JWT_ERROR_ATTRIBUTE, ERROR_INVALID_TOKEN);
            chain.doFilter(request, response);
            return;
        }

        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            errorResponseWriter.write(response, HttpStatus.FORBIDDEN, "ACCOUNT_" + user.getStatus(),
                    "This account is " + user.getStatus().name().toLowerCase(), request.getRequestURI());
            return;
        }

        CustomUserPrincipal principal = new CustomUserPrincipal(
                user.getId(), user.getEmail(), user.getDepartmentId(), user.getApplicantId());
        List<GrantedAuthority> authorities = permissionAuthorityResolver.resolveAuthorities(user);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }
}
