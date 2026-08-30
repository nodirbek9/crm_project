package uz.ithunter.crm.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.auth.JwtAuthenticationFilter;
import uz.ithunter.crm.shared.exception.ErrorResponseWriter;

/**
 * Runs at the filter-chain level (before {@code DispatcherServlet}), so it writes the standard
 * error body itself instead of going through {@code GlobalExceptionHandler}. Distinguishes
 * {@code 401 TOKEN_EXPIRED} from a plain missing/invalid token using the attribute
 * {@link JwtAuthenticationFilter} left on the request (S-01).
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    public CustomAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        boolean expired = JwtAuthenticationFilter.ERROR_TOKEN_EXPIRED
                .equals(request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE));
        String code = expired ? "TOKEN_EXPIRED" : "UNAUTHENTICATED";
        String message = expired ? "Access token has expired" : "Authentication is required";
        errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, code, message, request.getRequestURI());
    }
}
