package com.resilient.exception;

import com.resilient.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Centralized exception handler to create consistent, reactive error responses for the application.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* ---------- Exception Handlers ---------- */

    /** Handles custom UserNotFoundException and returns a 404 response. */
    @ExceptionHandler(UserNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), ex, false);
    }

    /** Handles custom UserAlreadyExistsException and returns a 409 response. */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage(), ex, false);
    }

    /** Handles JSON processing errors and returns a 400 response. */
    @ExceptionHandler(JsonProcessingRuntimeException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleJsonProcessingRuntimeException(
            JsonProcessingRuntimeException ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST, "JSON_PROCESSING_ERROR", ex.getMessage(), ex, false);
    }

    /** Handles DTO validation failures (@Valid) and returns a 400 response with field errors. */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationErrors(WebExchangeBindException ex) {
        // Access the reactive context to get the correlation ID for logging.
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault("correlationId", "N/A");

            // Collect all field validation errors into a single, readable string.
            String message = ex.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .collect(Collectors.joining(", "));

            log.warn("Validation error - correlationId: {}, errors: {}", correlationId, message);

            // Return a 400 Bad Request with a detailed error response body.
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            "VALIDATION_ERROR",
                            message,
                            correlationId,
                            Instant.now(),
                            ex.getBindingResult().getAllErrors()))); // Include detailed errors.
        });
    }

    /** Handles method-level validation failures (e.g., @RequestParam) and returns a 400 response. */
    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolation(ConstraintViolationException ex) {
        // Access the reactive context for the correlation ID.
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault("correlationId", "N/A");
            log.warn("Constraint violation - correlationId: {}, message: {}", correlationId, ex.getMessage());
            // Return a 400 Bad Request with the constraint violation message.
            return Mono.just(ResponseEntity.badRequest()
                    .body(new ErrorResponse(
                            "CONSTRAINT_VIOLATION", ex.getMessage(), correlationId, Instant.now(), null)));
        });
    }

    /** Handles reactive pipeline timeouts and returns a 408 response. */
    @ExceptionHandler(TimeoutException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTimeout(TimeoutException ex) {
        return buildErrorResponse(HttpStatus.REQUEST_TIMEOUT, "REQUEST_TIMEOUT", "Request timed out", ex, true);
    }

    /** Handles errors from outgoing WebClient calls, preserving the original status code. */
    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebClientResponse(WebClientResponseException ex) {
        return buildErrorResponse(
                HttpStatus.valueOf(ex.getStatusCode().value()), "WEBCLIENT_ERROR", ex.getMessage(), ex, true);
    }

    /** A catch-all handler for any other unhandled exceptions, returning a 500 response. */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", ex, true);
    }

    /* ---------- Helper Method ---------- */

    /** Helper method to build a standardized ErrorResponse with logging and correlation ID. */
    private Mono<ResponseEntity<ErrorResponse>> buildErrorResponse(
            HttpStatus status, String code, String message, Throwable ex, boolean isServerError) {
        // Defer execution to access the reactive context.
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault("correlationId", "N/A");

            // Log server-side errors with a full stack trace, and client-side errors with a warning.
            if (isServerError) {
                log.error("{} - correlationId: {}", code, correlationId, ex);
            } else {
                log.warn("{} - correlationId: {}, message: {}", code, correlationId, message);
            }

            // Build and return the standardized error response entity.
            return Mono.just(ResponseEntity.status(status)
                    .body(new ErrorResponse(code, message, correlationId, Instant.now(), null)));
        });
    }
}
