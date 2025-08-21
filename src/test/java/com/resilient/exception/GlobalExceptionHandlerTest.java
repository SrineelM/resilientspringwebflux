package com.resilient.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.resilient.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GlobalExceptionHandlerTest {
    @Test
    void handleUserNotFound_happyPath() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        UserNotFoundException ex = new UserNotFoundException("User not found");
        Mono<ResponseEntity<ErrorResponse>> result = handler.handleUserNotFound(ex);
        StepVerifier.create(result)
                .assertNext(resp -> {
                    assertEquals(404, resp.getStatusCode().value());
                    assertEquals("USER_NOT_FOUND", resp.getBody().code());
                    assertEquals("User not found", resp.getBody().message());
                })
                .verifyComplete();
    }
}
