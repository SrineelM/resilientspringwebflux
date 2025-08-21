package com.resilient.service;

import com.resilient.dto.UserRequest;
import com.resilient.dto.UserResponse;
import com.resilient.exception.UserAlreadyExistsException;
import com.resilient.exception.UserNotFoundException;
import com.resilient.model.User;
import com.resilient.ports.UserAuditPort;
import com.resilient.ports.UserNotificationPort;
import com.resilient.repository.UserRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service class for user-related business logic. Handles user creation, validation, notification,
 * and auditing in a reactive and resilient manner.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserNotificationPort notificationPort;
    private final UserAuditPort auditPort;

    public UserService(
            UserRepository userRepository,
            @Qualifier("notificationAdapter") UserNotificationPort notificationPort,
            UserAuditPort auditPort) {
        this.userRepository = userRepository;
        this.notificationPort = notificationPort;
        this.auditPort = auditPort;
    }

    @Observed(name = "user.create")
    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackCreateUser")
    @Retry(name = "userService")
    @Bulkhead(name = "userService")
    @Transactional
    public Mono<UserResponse> createUser(UserRequest request) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault("correlationId", "N/A");

            return validateUserUniqueness(request)
                    .then(userRepository.save(request.toUser()))
                    .flatMap(user -> processNewUser(user, correlationId))
                    .map(UserResponse::from)
                    .contextWrite(ctx)
                    .doOnSuccess(user -> log.info("[{}] User created: {}", correlationId, user.username()))
                    .doOnError(error -> log.error("[{}] Create user failed", correlationId, error));
        });
    }

    private Mono<User> processNewUser(User user, String correlationId) {
        return Mono.deferContextual(ctx -> {
            String prefsChannel = "email";
            com.resilient.ports.dto.NotificationPreferences prefs =
                    new com.resilient.ports.dto.NotificationPreferences(prefsChannel, true, false);

            Mono<com.resilient.ports.dto.NotificationResult> notification = notificationPort
                    .sendWelcomeNotification(correlationId, user, prefs)
                    .onErrorResume(e -> {
                        log.warn("[{}] Notification failed: {}", correlationId, e.getMessage());
                        return Mono.just(com.resilient.ports.dto.NotificationResult.failed("Notification failed"));
                    });

            Mono<com.resilient.ports.dto.AuditResult> audit = auditPort
                    .auditUserAction(correlationId, "CREATE", user, java.util.Collections.emptyMap())
                    .onErrorResume(e -> {
                        log.warn("[{}] Audit log failed: {}", correlationId, e.getMessage());
                        return Mono.just(com.resilient.ports.dto.AuditResult.fallback("audit_failed"));
                    });

            return Mono.zip(notification, audit).thenReturn(user).contextWrite(ctx);
        });
    }

    @Observed(name = "user.find.by.id")
    @CircuitBreaker(name = "userService")
    public Mono<UserResponse> findById(Long id) {
        return userRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found with id: " + id)))
                .map(UserResponse::from)
                .doOnNext(user -> log.debug("Found user: {}", user.username()));
    }

    @Observed(name = "user.find.all")
    public Flux<UserResponse> findAll() {
        return userRepository.findAll().map(UserResponse::from).doOnComplete(() -> log.debug("Retrieved all users"));
    }

    @Observed(name = "user.search")
    public Flux<UserResponse> searchUsers(String query) {
        String pattern = "%" + query + "%";
        return userRepository
                .findByUsernameOrEmailContaining(pattern)
                .map(UserResponse::from)
                .doOnComplete(() -> log.debug("Search completed for query: {}", query));
    }

    @Observed(name = "user.update.status")
    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackUpdateUserStatus")
    @Bulkhead(name = "userService")
    @Transactional
    public Mono<UserResponse> updateUserStatus(Long id, User.UserStatus status) {
        return userRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found with id: " + id)))
                .flatMap(user -> updateStatusWithNotification(user, status))
                .map(UserResponse::from);
    }

    private Mono<UserResponse> fallbackUpdateUserStatus(Long id, User.UserStatus status, Throwable t) {
        log.warn("Fallback: returning cached user for update status {} due to error: {}", id, t.getMessage());
        return Mono.just(UserResponse.from(User.create("cachedUser", "cached@example.com", "Cached User")));
    }

    /**
     * Deletes a user by ID, auditing the action and returning true if deleted, false if not found.
     */
    @Observed(name = "user.delete")
    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackDeleteUser")
    @Transactional
    public Mono<Boolean> deleteUser(Long id) {
        return userRepository
                .findById(id)
                .flatMap(user -> Mono.deferContextual(ctx -> {
                    String correlationId = ctx.getOrDefault("correlationId", "N/A");
                    return auditPort
                            .auditUserAction(correlationId, "DELETE", user, java.util.Collections.emptyMap())
                            .onErrorResume(e -> {
                                log.warn("[{}] Audit failed for delete: {}", correlationId, e.getMessage());
                                return Mono.just(com.resilient.ports.dto.AuditResult.fallback("audit_failed"));
                            })
                            .then(userRepository.deleteById(id))
                            .thenReturn(true);
                }))
                .defaultIfEmpty(false)
                .doOnSuccess(deleted -> {
                    if (deleted) {
                        log.info("User deleted: {}", id);
                    } else {
                        log.info("User not found for deletion: {}", id);
                    }
                });
    }

    private Mono<Boolean> fallbackDeleteUser(Long id, Throwable t) {
        log.error("Fallback: unable to delete user {} due to {}", id, t.getMessage());
        return Mono.just(false);
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackGetUser")
    public Mono<UserResponse> getUser(Long id) {
        return userRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found with id: " + id)))
                .map(UserResponse::from)
                .doOnNext(user -> log.debug("Found user: {}", user.username()));
    }

    private Mono<UserResponse> fallbackGetUser(Long id, Throwable t) {
        log.warn("Fallback: retrieving user {} from cache due to error: {}", id, t.getMessage());
        return Mono.just(UserResponse.from(User.create("cachedUser", "cached@example.com", "Cached User")))
                .doOnError(e -> log.error("Cache fallback failed for user {}: {}", id, e.getMessage()));
    }

    private Mono<Void> validateUserUniqueness(UserRequest request) {
        if (request == null || request.username() == null || request.email() == null) {
            return Mono.error(new IllegalArgumentException("Request, username, or email cannot be null"));
        }

        return Mono.zip(
                        userRepository.existsByUsername(request.username()),
                        userRepository.existsByEmail(request.email()))
                .flatMap(tuple -> {
                    boolean usernameExists = tuple.getT1();
                    boolean emailExists = tuple.getT2();

                    if (usernameExists) {
                        return Mono.error(
                                new UserAlreadyExistsException("Username already exists: " + request.username()));
                    }
                    if (emailExists) {
                        return Mono.error(new UserAlreadyExistsException("Email already exists: " + request.email()));
                    }
                    return Mono.empty();
                });
    }

    private Mono<User> updateStatusWithNotification(User user, User.UserStatus newStatus) {
        User updatedUser = user.withStatus(newStatus);

        return userRepository
                .save(updatedUser)
                .flatMap(savedUser -> Mono.deferContextual(ctx -> {
                    String correlationId = ctx.getOrDefault("correlationId", "N/A");

                    Mono<com.resilient.ports.dto.NotificationResult> statusNotification = notificationPort
                            .sendStatusUpdate(
                                    correlationId,
                                    savedUser,
                                    savedUser.status().name(),
                                    java.util.Collections.emptyMap())
                            .onErrorResume(error -> {
                                log.warn(
                                        "[{}] Status notification failed for user: {}",
                                        correlationId,
                                        savedUser.username());
                                return Mono.just(
                                        com.resilient.ports.dto.NotificationResult.failed("notification-failed"));
                            });

                    Mono<com.resilient.ports.dto.AuditResult> auditLog = auditPort
                            .auditUserAction(
                                    correlationId, "STATUS_UPDATE", savedUser, java.util.Collections.emptyMap())
                            .onErrorResume(error -> {
                                log.warn(
                                        "[{}] Audit failed for status update of user {}: {}",
                                        correlationId,
                                        savedUser.username(),
                                        error.getMessage());
                                return Mono.just(com.resilient.ports.dto.AuditResult.fallback("audit_failed"));
                            });

                    return Mono.zip(statusNotification, auditLog).thenReturn(savedUser);
                }));
    }

    public Mono<UserResponse> fallbackCreateUser(UserRequest request, Throwable t) {
        log.error("Fallback: failed to create user for request {}: {}", request, t.getMessage());
        return Mono.error(new RuntimeException("User creation failed due to service unavailability."));
    }

    public Mono<ResponseEntity<UserResponse>> updateUser(Long id, UserRequest request) {
        throw new UnsupportedOperationException("Unimplemented method 'updateUser'");
    }
}
