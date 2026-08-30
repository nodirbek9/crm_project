package uz.ithunter.crm.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The single {@code @RestControllerAdvice} for the exception hierarchy described in
 * API_SPEC.md 9. Filter-chain-level 401/403 (missing/expired token, insufficient permission) are
 * handled separately by {@code CustomAuthenticationEntryPoint}/{@code CustomAccessDeniedHandler}
 * since they run before {@code DispatcherServlet} and never reach this class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ConstraintViolationTranslator constraintViolationTranslator;

    public GlobalExceptionHandler(ConstraintViolationTranslator constraintViolationTranslator) {
        this.constraintViolationTranslator = constraintViolationTranslator;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        List<FieldIssue> details = ex instanceof ValidationException ve ? ve.getDetails() : List.of();
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, details);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldIssue> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldIssue(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, details);
    }

    // @PreAuthorize denials (AuthorizationDeniedException extends this) are thrown INSIDE the
    // controller invocation, i.e. inside DispatcherServlet, so they never reach
    // CustomAccessDeniedHandler (which only sees filter-chain-level denials, before dispatch).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                "You do not have permission to perform this action", request, List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was modified by another request; reload and retry", request, List.of());
    }

    // Phase 12 (C-01, C-02): a genuine PostgreSQL deadlock between two racing transactions - e.g.
    // both sides hold a row's implicit ShareLock (from an FK check on an unrelated insert in the
    // same flush) and each then tries to upgrade to Exclusive for their own pending UPDATE of that
    // row. This is not a bug in one specific code path; it is an inherent risk of optimistic
    // locking plus audit-writing in the same transaction, so it is handled centrally rather than
    // by re-ordering flushes in every service method that could theoretically hit it. Semantically
    // this is the exact same situation as losing an @Version race: reload and retry.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleDeadlockOrLockTimeout(PessimisticLockingFailureException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was locked by another concurrent request; reload and retry", request, List.of());
    }

    // Phase 12: named uq_* constraints/indexes translate to a specific business code via
    // ConstraintViolationTranslator (the DB-level backstop for races the service layer's own
    // pre-checks cannot fully prevent). Anything unmapped - notably every ck_* CHECK constraint,
    // deliberately excluded - falls back to this generic, still-never-a-500 response.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        return constraintViolationTranslator.translate(ex)
                .map(mapping -> build(mapping.status(), mapping.code(), mapping.message(), request, List.of()))
                .orElseGet(() -> {
                    log.warn("Unmapped data integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
                    return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                            "The request conflicts with an existing record or business rule", request, List.of());
                });
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected error, traceId={}", traceId, ex);
        ErrorResponse body = new ErrorResponse(
                Instant.now(), 500, HttpStatus.INTERNAL_SERVER_ERROR.name(), "INTERNAL_ERROR",
                "An unexpected error occurred", request.getRequestURI(), traceId, List.of());
        return ResponseEntity.internalServerError().body(body);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request, List<FieldIssue> details) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.name(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), details);
        return ResponseEntity.status(status).body(body);
    }
}
