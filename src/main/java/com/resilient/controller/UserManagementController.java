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
 * A production-grade, resilient controller for managing user data.
 *
 * <p>This controller exposes RESTful endpoints for CRUD (Create, Read, Update, Delete) operations
 * on users. It is secured using method-level authorization with {@code @PreAuthorize} and employs
 * resilience patterns like Circuit Breakers and Time Limiters from Resilience4j to ensure
 * stability and graceful degradation under load or partial failure.
 */
@RestController
@RequestMapping("/api/users") // Base path for all endpoints in this controller.
@Validated // Enables method-level validation for parameters (e.g., @Min, @NotBlank).
@Observed // Enables Micrometer Observation for all endpoints, providing metrics and traces.
public class UserManagementController {

    // Logger for this controller.
    private static final Logger log = LoggerFactory.getLogger(UserManagementController.class);
    private final UserService userService;

    /**
     * Constructs the controller with the required {@link UserService}.
     * @param userService The service that encapsulates user-related business logic.
     */
    public UserManagementController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Creates a new user.
     * This endpoint is protected and only accessible by users with the 'ADMIN' role.
     * It is wrapped in a circuit breaker and a time limiter to protect against downstream service failures.
     *
     * @param request The user creation request, validated to ensure all fields are present.
     * @return A {@link Mono} of {@link ResponseEntity}. On success, returns 201 Created with the new user's data.
     *         On failure, the circuit breaker may trigger the fallback method.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") // Security: Only admins can create users.
    @CircuitBreaker(name = "user-service", fallbackMethod = "createUserFallback") // Resilience: Isolates from repeated failures.
    @TimeLimiter(name = "user-service") // Resilience: Prevents requests from waiting indefinitely.
    public Mono<ResponseEntity<UserResponse>> createUser(@Valid @RequestBody UserRequest request) {
        log.info("Creating user with username: {}", request.username());
        // Start the reactive pipeline by calling the user service.
        return userService
                .createUser(request)
                // Set a hard 10-second timeout for the entire operation.
                .timeout(Duration.ofSeconds(10))
                // If successful, map the created user to an HTTP 201 Created response.
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(user))
                // If any error occurs (e.g., timeout, service exception), handle it gracefully.
                .onErrorResume(ex -> handleServiceError("createUser", ex));
    }

    /**
     * Fallback method for {@link #createUser(UserRequest)}.
     * This is invoked by the circuit breaker when it's open or when the primary method fails.
     *
     * @param request The original request object.
     * @param ex The exception that triggered the fallback.
     * @return A {@link Mono} with an HTTP 503 Service Unavailable response.
     */
    private Mono<ResponseEntity<UserResponse>> createUserFallback(UserRequest request, Throwable ex) {
        log.error("Fallback triggered for createUser: {}", ex.getMessage(), ex);
        // Return a response indicating that the service is temporarily unavailable.
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null));
    }

    /**
     * Retrieves a user by their unique ID.
     * Accessible by users with either 'ADMIN' or 'USER' roles.
     *
     * @param id The ID of the user to retrieve. Must be a positive number.
     * @return A {@link Mono} of {@link ResponseEntity}. Returns 200 OK with the user data if found,
     *         404 Not Found if not, or 503 Service Unavailable if the fallback is triggered.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')") // Security: Admins or regular users can view user details.
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserFallback") // Resilience: Protects the endpoint.
    public Mono<ResponseEntity<UserResponse>> getUserById(@PathVariable @Min(1) @NotNull Long id) {
        // Call the service to find the user by ID.
        return userService
                .findById(id)
                // Set a 5-second timeout for this operation.
                .timeout(Duration.ofSeconds(5))
                // If a user is found, wrap it in an HTTP 200 OK response.
                .map(ResponseEntity::ok)
                // If the service returns an empty Mono, it means the user was not found. Return 404.
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                // Handle any other errors that might occur.
                .onErrorResume(ex -> handleServiceError("getUserById", ex));
    }

    /**
     * Fallback method for {@link #getUserById(Long)}.
     *
     * @param id The original user ID.
     * @param ex The exception that triggered the fallback.
     * @return A {@link Mono} with an HTTP 503 Service Unavailable response.
     */
    private Mono<ResponseEntity<UserResponse>> getUserFallback(Long id, Throwable ex) {
        log.error("Fallback triggered for getUserById: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null));
    }

    /**
     * Updates an existing user's details.
     * Only accessible by users with the 'ADMIN' role.
     *
     * @param id The ID of the user to update.
     * @param request The request body with the updated user information.
     * @return A {@link Mono} of {@link ResponseEntity}. Returns 200 OK with the updated user data,
     *         or 404 Not Found if the user doesn't exist.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')") // Security: Only admins can update user details.
    public Mono<ResponseEntity<UserResponse>> updateUser(
            @PathVariable @Min(1) @NotNull Long id, @Valid @RequestBody UserRequest request) {

        // Call the service to update the user.
        return userService
                .updateUser(id, request)
                // If the service returns an empty Mono, the user was not found.
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Retrieves a list of all users.
     * Only accessible by users with the 'ADMIN' role.
     *
     * @return A {@link Flux} of {@link UserResponse} objects.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')") // Security: Only admins can list all users.
    public Flux<UserResponse> getAllUsers() {
        return userService.findAll();
    }

    /**
     * Streams all users as Server-Sent Events (SSE).
     * This is useful for real-time dashboards or monitoring.
     * Only accessible by users with the 'ADMIN' role.
     *
     * @return A {@link Flux} of {@link UserResponse} objects, formatted as an SSE stream.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')") // Security: Only admins can access the user stream.
    public Flux<UserResponse> getAllUsersStream() {
        // Get all users from the service.
        return userService
                .findAll()
                // Add a small delay between each emitted event to simulate a real-time stream.
                .delayElements(Duration.ofMillis(500))
                // As a side-effect, log each user being streamed for debugging.
                .doOnNext(user -> log.debug("Streaming user: {}", user.username()));
    }

    /**
     * Searches for users based on a query string.
     * Accessible by users with either 'ADMIN' or 'USER' roles.
     *
     * @param query The search term. Must be between 2 and 50 characters.
     * @return A {@link Flux} of matching {@link UserResponse} objects.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','USER')") // Security: Admins and users can search.
    public Flux<UserResponse> searchUsers(@RequestParam @NotBlank @Size(min = 2, max = 50) String query) {

        // Sanitize the input query to remove leading/trailing whitespace.
        String sanitizedQuery = query.trim();
        log.info("Searching users with sanitized query: {}", sanitizedQuery);
        // Call the service to perform the search.
        return userService.searchUsers(sanitizedQuery);
    }

    /**
     * Updates the status of a specific user (e.g., ACTIVE, INACTIVE).
     * Only accessible by users with the 'ADMIN' role.
     *
     * @param id The ID of the user to update.
     * @param status The new status for the user.
     * @return A {@link Mono} of {@link ResponseEntity}. Returns 200 OK with the updated user data,
     *         or 404 Not Found if the user doesn't exist.
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')") // Security: Only admins can change a user's status.
    public Mono<ResponseEntity<UserResponse>> updateUserStatus(
            @PathVariable @Min(1) @NotNull Long id, @RequestParam @NotNull User.UserStatus status) {

        // Call the service to update the user's status.
        return userService
                .updateUserStatus(id, status)
                // If successful, wrap the updated user in an HTTP 200 OK response.
                .map(ResponseEntity::ok)
                // If the user is not found, return an HTTP 404 Not Found response.
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Deletes a user by their ID.
     * Only accessible by users with the 'ADMIN' role.
     *
     * @param id The ID of the user to delete.
     * @return A {@link Mono} of {@link ResponseEntity}. Returns 204 No Content on successful deletion,
     *         or 404 Not Found if the user did not exist.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')") // Security: Only admins can delete users.
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable @Min(1) @NotNull Long id) {
        // Call the service to delete the user.
        return userService
                .deleteUser(id)
                // The service returns a boolean. Map it to an appropriate HTTP response.
                .map(deleted -> deleted
                        ? ResponseEntity.noContent().<Void>build() // If true, return 204 No Content.
                        : ResponseEntity.notFound().<Void>build()) // If false, return 404 Not Found.
                // If the service returns an empty Mono for some reason, default to 404.
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * A centralized error handler for this controller's reactive pipelines.
     * Logs the error and returns a generic HTTP 500 Internal Server Error response.
     *
     * @param operation The name of the operation that failed (for logging).
     * @param ex The exception that occurred.
     * @return A {@link Mono} containing a generic error response.
     */
    private <T> Mono<ResponseEntity<T>> handleServiceError(String operation, Throwable ex) {
        log.error("Error during {}: {}", operation, ex.getMessage(), ex);
        // Return a standard 500 error to the client, hiding internal details.
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
    }
}
