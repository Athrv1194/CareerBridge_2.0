package com.careerbridge.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * One exception carrying its own status, matching every other service in this project.
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
