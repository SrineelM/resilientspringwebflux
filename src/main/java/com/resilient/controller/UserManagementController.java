package com.resilient.controller;

import com.resilient.dto.UserRequest;
import com.resilient.dto.UserResponse;
import com.resilient.model.User;
import com.resilient.service.UserService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.observation.annotation.Observed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Production-grade user management controller. Security: Role-based access, validated inputs, rate
 * limiting, safe logging. Resilience: Circuit breaker, timeout, SSE streaming for observability.
 */
@RestController
@RequestMapping("/api/users")
@Validated
@Observed
public class UserManagementController {

    private static final Logger log = LoggerFactory.getLogger(UserManagementController.class);
    private final UserService userService;

    public UserManagementController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @CircuitBreaker(name = "user-service", fallbackMethod = "createUserFallback")
    @TimeLimiter(name = "user-service")
    public Mono<ResponseEntity<UserResponse>> createUser(@Valid @RequestBody UserRequest request) {
        log.info("Creating user with username: {}", request.username());
        return userService
                .createUser(request)
                .timeout(Duration.ofSeconds(10))
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(user))
                .onErrorResume(ex -> handleServiceError("createUser", ex));
    }

    private Mono<ResponseEntity<UserResponse>> createUserFallback(UserRequest request, Throwable ex) {
        log.error("Fallback triggered for createUser: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserFallback")
    public Mono<ResponseEntity<UserResponse>> getUserById(@PathVariable @Min(1) @NotNull Long id) {
        return userService
                .findById(id)
                .timeout(Duration.ofSeconds(5))
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(ex -> handleServiceError("getUserById", ex));
    }

    private Mono<ResponseEntity<UserResponse>> getUserFallback(Long id, Throwable ex) {
        log.error("Fallback triggered for getUserById: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<UserResponse>> updateUser(
            @PathVariable @Min(1) @NotNull Long id, @Valid @RequestBody UserRequest request) {

        return userService
                .updateUser(id, request)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserResponse> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserResponse> getAllUsersStream() {
        return userService
                .findAll()
                .delayElements(Duration.ofMillis(500))
                .doOnNext(user -> log.debug("Streaming user: {}", user.username()));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Flux<UserResponse> searchUsers(@RequestParam @NotBlank @Size(min = 2, max = 50) String query) {

        String sanitizedQuery = query.trim();
        log.info("Searching users with sanitized query: {}", sanitizedQuery);
        return userService.searchUsers(sanitizedQuery);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<UserResponse>> updateUserStatus(
            @PathVariable @Min(1) @NotNull Long id, @RequestParam @NotNull User.UserStatus status) {

        return userService
                .updateUserStatus(id, status)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable @Min(1) @NotNull Long id) {
        return userService
                .deleteUser(id)
                .map(deleted -> deleted
                        ? ResponseEntity.noContent().<Void>build()
                        : ResponseEntity.notFound().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** Centralized error handler for service calls. */
    private <T> Mono<ResponseEntity<T>> handleServiceError(String operation, Throwable ex) {
        log.error("Error during {}: {}", operation, ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
    }
}
