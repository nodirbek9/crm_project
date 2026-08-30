package uz.ithunter.crm.shared.exception;

import org.springframework.http.HttpStatus;

public class AccessDeniedDomainException extends DomainException {

    public AccessDeniedDomainException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
