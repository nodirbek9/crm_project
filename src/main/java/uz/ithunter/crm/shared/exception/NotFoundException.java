package uz.ithunter.crm.shared.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public NotFoundException(String message) {
        this("RESOURCE_NOT_FOUND", message);
    }
}
