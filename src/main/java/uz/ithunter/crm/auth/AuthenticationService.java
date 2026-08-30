package uz.ithunter.crm.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ithunter.crm.auth.dto.TokenResponse;
import uz.ithunter.crm.auth.dto.UserSummary;
import uz.ithunter.crm.shared.exception.AccessDeniedDomainException;
import uz.ithunter.crm.shared.exception.AuthenticationFailedException;
import uz.ithunter.crm.user.Role;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserRepository;
import uz.ithunter.crm.user.UserStatus;

/**
 * Login/refresh/logout. API_SPEC.md 1: {@code 401} on bad credentials, {@code 403} on
 * BLOCKED/DISABLED. {@code login} goes through the standard {@code AuthenticationManager} /
 * {@link AppUserDetailsService} flow (FINAL_IMPLEMENTATION_ORDER.md Phase 3's "UserDetails
 * adapter") purely for the credential + account-status check - the resulting
 * {@link AppUserDetails} carries no authorities, since permissions are resolved fresh per request
 * by {@link PermissionAuthorityResolver}, never at login time. Refresh-token rotation and reuse
 * detection are out of scope (SECURITY_SPEC.md 10, [DEMO] - see ASSUMPTIONS.md): {@code refresh}
 * simply validates the presented token and mints a fresh pair, and {@code logout} is a stateless
 * no-op since there is no server-side refresh-token store to invalidate.
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthenticationService(UserRepository userRepository, AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String rawPassword) {
        try {
            var authResult = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));
            User user = ((AppUserDetails) authResult.getPrincipal()).getUser();
            return buildTokenResponse(user);
        } catch (LockedException ex) {
            throw new AccessDeniedDomainException("ACCOUNT_BLOCKED", "This account is blocked");
        } catch (DisabledException ex) {
            throw new AccessDeniedDomainException("ACCOUNT_DISABLED", "This account is disabled");
        } catch (BadCredentialsException ex) {
            throw new AuthenticationFailedException("UNAUTHENTICATED", "Invalid email or password");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        var claims = parseRefreshTokenOrThrow(refreshToken);
        if (!JwtService.TYPE_REFRESH.equals(jwtService.extractType(claims))) {
            throw new AuthenticationFailedException("UNAUTHENTICATED", "Not a refresh token");
        }

        User user = userRepository.findById(jwtService.extractUserId(claims))
                .orElseThrow(() -> new AuthenticationFailedException("UNAUTHENTICATED", "Invalid refresh token"));
        requireActive(user);

        return buildTokenResponse(user);
    }

    private io.jsonwebtoken.Claims parseRefreshTokenOrThrow(String refreshToken) {
        try {
            return jwtService.parseOrThrow(refreshToken);
        } catch (ExpiredJwtException ex) {
            throw new AuthenticationFailedException("TOKEN_EXPIRED", "Refresh token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthenticationFailedException("UNAUTHENTICATED", "Invalid refresh token");
        }
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedDomainException("ACCOUNT_" + user.getStatus(),
                    "This account is " + user.getStatus().name().toLowerCase());
        }
    }

    private TokenResponse buildTokenResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).map(Enum::name).toList();
        UserSummary summary = new UserSummary(user.getId(), user.getEmail(), user.getFullName(), roleCodes);
        return new TokenResponse(accessToken, refreshToken, jwtService.getAccessTokenTtl().toSeconds(), summary);
    }
}
