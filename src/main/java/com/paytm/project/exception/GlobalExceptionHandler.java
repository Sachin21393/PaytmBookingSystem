package com.paytm.project.exception;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatConflictException.class)
    public ResponseEntity<Map<String, Object>> handleSeatConflict(SeatConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, "SEAT_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidIdempotencyKey(InvalidIdempotencyKeyException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IDEMPOTENCY_KEY", ex.getMessage());
    }

    @ExceptionHandler(UserLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUserLimitExceeded(UserLimitExceededException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "USER_LIMIT_EXCEEDED", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler({
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class
    })
    public ResponseEntity<Map<String, Object>> handleLockContention(Exception ex) {
        return buildResponse(HttpStatus.CONFLICT, "LOCK_CONTENTION", "Could not acquire seat lock due to high concurrency. Please retry.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(org.springframework.web.bind.MissingRequestHeaderException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Missing required header: " + ex.getHeaderName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
