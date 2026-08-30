package uz.ithunter.crm.shared.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ValidationException extends DomainException {

    private final List<FieldIssue> details;

    public ValidationException(String code, String message) {
        this(code, message, List.of());
    }

    public ValidationException(String code, String message, List<FieldIssue> details) {
        super(HttpStatus.BAD_REQUEST, code, message);
        this.details = details;
    }

    public List<FieldIssue> getDetails() {
        return details;
    }
}
