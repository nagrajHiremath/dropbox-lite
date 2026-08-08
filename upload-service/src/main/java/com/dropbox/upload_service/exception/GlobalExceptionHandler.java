package com.dropbox.upload_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed", Instant.now(), fieldErrors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage(), Instant.now(), null));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("IDEMPOTENCY_KEY_CONFLICT", ex.getMessage(), Instant.now(), null));
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDependencyUnavailable(DependencyUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("DEPENDENCY_UNAVAILABLE", ex.getMessage(), Instant.now(), null));
    }

    @ExceptionHandler(InvalidUploadStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUploadState(InvalidUploadStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_UPLOAD_STATE", ex.getMessage(), Instant.now(), null));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", ex.getMessage(), Instant.now(), null));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(VersionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("VERSION_CONFLICT", ex.getMessage(), Instant.now(), null));
    }
}
