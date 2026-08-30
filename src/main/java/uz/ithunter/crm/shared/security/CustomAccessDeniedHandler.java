package uz.ithunter.crm.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.shared.exception.ErrorResponseWriter;

/** Authenticated but lacking the required permission (e.g. {@code @PreAuthorize} failure) - {@code 403 PERMISSION_DENIED}. */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    public CustomAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        errorResponseWriter.write(response, HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "You do not have permission to perform this action", request.getRequestURI());
    }
}
