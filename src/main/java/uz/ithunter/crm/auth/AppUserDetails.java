package uz.ithunter.crm.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import uz.ithunter.crm.user.User;
import uz.ithunter.crm.user.UserStatus;

/**
 * {@link UserDetails} adapter over {@link User} (FINAL_IMPLEMENTATION_ORDER.md Phase 3), used only
 * to let {@code AuthenticationManager}/{@code DaoAuthenticationProvider} perform the login-time
 * credential + account-status check. It deliberately carries NO permission-derived authorities -
 * those are resolved fresh per request by {@link PermissionAuthorityResolver}, never cached here
 * or in a token (SECURITY_SPEC.md 1).
 */
public class AppUserDetails implements UserDetails {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.BLOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}
