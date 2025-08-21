package com.resilient.exception;

import com.resilient.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* ---------- Exception Handlers ---------- */

    @ExceptionHandler(UserNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), ex, false);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage(), ex, false);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, ex, false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolation(ConstraintViolationException ex) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault("correlationId", "N/A");
            log.warn("Constraint violation - correlationId: {}, message: {}", correlationId, ex.getMessage());
            return Mono.just(ResponseEntity.badRequest()
                    .body(new ErrorResponse(
                            "CONSTRAINT_VIOLATION", ex.getMessage(), correlationId, Instant.now(), null)));
        });
    }

    @ExceptionHandler(TimeoutException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTimeout(TimeoutException ex) {
        return buildErrorResponse(HttpStatus.REQUEST_TIMEOUT, "REQUEST_TIMEOUT", "Request timed out", ex, true);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebClientResponse(WebClientResponseException ex) {
        return buildErrorResponse(
                HttpStatus.valueOf(ex.getStatusCode().value()), "WEBCLIENT_ERROR", ex.getMessage(), ex, true);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", ex, true);
    }

    /* ---------- Helper Method ---------- */

    private Mono<ResponseEntity<ErrorResponse>> buildErrorResponse(
            HttpStatus status, String code, String message, Throwable ex, boolean isServerError) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault("correlationId", "N/A");

            if (isServerError) {
                log.error("{} - correlationId: {}", code, correlationId, ex);
            } else {
                log.warn("{} - correlationId: {}, message: {}", code, correlationId, message);
            }

            return Mono.just(ResponseEntity.status(status)
                    .body(new ErrorResponse(code, message, correlationId, Instant.now(), null)));
        });
    }
}
