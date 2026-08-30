package uz.ithunter.crm.shared.exception;

import org.springframework.http.HttpStatus;

public class IllegalStateTransitionException extends DomainException {

    public IllegalStateTransitionException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
