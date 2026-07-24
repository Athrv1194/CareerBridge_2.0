package com.careerbridge.auth.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(CustomException ex) {
        return build(ex.getStatus(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    /**
     * Unparseable request body. Not covered by the ErrorResponse branch below:
     * HttpMessageNotReadableException extends HttpMessageConversionException and, unlike
     * NoResourceFoundException et al., was never retrofitted with the ErrorResponse interface --
     * so without this handler a malformed body is reported as a 500 server fault.
     * The parse detail is withheld: it echoes request content back to an unauthenticated caller.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception ex) {
        // Spring's own MVC failures (404, 405, 415, malformed JSON) already carry the right
        // status. Without this they would all be flattened into a misleading 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return build(HttpStatus.valueOf(errorResponse.getStatusCode().value()), ex.getMessage(), null);
        }

        // Logged in full, but never echoed to the client: stack traces and SQL leak internals.
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Object errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errors", errors);
        body.put("status", status.value());
        body.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(status).body(body);
    }
}
