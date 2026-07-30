package com.careerbridge.roadmap.exception;

import org.springframework.http.HttpStatus;

/**
 * One exception carrying its own status, matching the other seven services. Deliberately not a
 * family of ResourceNotFound/Duplicate/AccessDenied types: GlobalExceptionHandler would need a
 * handler per class to do what one already does, and an AccessDeniedException here would shadow
 * Spring Security's identically-named class for anyone reading imports in a hurry.
 */
public class CustomException extends RuntimeException {

    private final HttpStatus status;

    public CustomException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
