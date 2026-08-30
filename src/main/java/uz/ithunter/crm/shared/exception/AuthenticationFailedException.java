package uz.ithunter.crm.shared.exception;

import org.springframework.http.HttpStatus;

/** 401 raised inside a normal controller/service call (e.g. bad login credentials), as opposed to
 * filter-level auth failures which never reach {@code @RestControllerAdvice}. */
public class AuthenticationFailedException extends DomainException {

    public AuthenticationFailedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
