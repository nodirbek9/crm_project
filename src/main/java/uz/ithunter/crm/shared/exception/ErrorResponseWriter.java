package uz.ithunter.crm.shared.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds and writes the standard {@link ErrorResponse} JSON body. Shared by
 * {@link GlobalExceptionHandler} (controller-level exceptions) and the filter-chain-level
 * entry point / access-denied handler / {@code JwtAuthenticationFilter}, which run outside
 * {@code @RestControllerAdvice}'s reach and so cannot go through the handler.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message, String path)
            throws IOException {
        write(response, status, code, message, path, List.of());
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message, String path,
            List<FieldIssue> details) throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.name(),
                code,
                message,
                path,
                UUID.randomUUID().toString(),
                details);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
