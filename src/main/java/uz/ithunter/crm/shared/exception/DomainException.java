package uz.ithunter.crm.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the domain exception hierarchy from API_SPEC.md 9. A single {@code @RestControllerAdvice}
 * ({@link GlobalExceptionHandler}) translates every subtype to the standard error body.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
