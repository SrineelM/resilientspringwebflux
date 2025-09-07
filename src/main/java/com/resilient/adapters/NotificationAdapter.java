package com.resilient.adapters;

import com.resilient.model.User;
import com.resilient.ports.UserNotificationPort;
import com.resilient.ports.dto.NotificationPreferences;
import com.resilient.ports.dto.NotificationResult;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Adapter implementation for sending notifications to users.
 *
 * <p>This class is a "secondary" or "driven" adapter in Hexagonal Architecture. It implements the
 * UserNotificationPort and encapsulates the logic for interacting with an external notification
 * service.
 *
 * <p>It uses Resilience4j annotations for circuit breaking, retry, and bulkhead isolation, and
 * simulates a flaky notification service with random failures and delays for demonstration.
 */
@Service
public class NotificationAdapter implements UserNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdapter.class);
    private final Random random = new Random();
    private final Scheduler scheduler;

    /**
     * Constructs a NotificationAdapter with a specific Reactor scheduler.
     *
     * @param scheduler The scheduler to use for async notification tasks, injected via @Qualifier to
     *     ensure a dedicated thread pool for notification operations.
     */
    public NotificationAdapter(@Qualifier("notificationScheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sends a welcome notification to the given user.
     *
     * <p>This method is protected by several resilience patterns:
     *
     * <ul>
     *   <li><b>@CircuitBreaker:</b> If the notification service fails repeatedly, the circuit will
     *       "open" and immediately fail-fast by calling the fallback method, preventing system
     *       overload.
     *   <li><b>@Retry:</b> Automatically retries the operation on transient failures.
     *   <li><b>@Bulkhead:</b> Limits the number of concurrent calls to this method, preventing it
     *       from consuming all available threads.
     * </ul>
     *
     * @param user The user to notify.
     * @return a Mono emitting a success message or error
     */
    @Override
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackSendWelcome")
    @Retry(name = "notificationService")
    @Bulkhead(name = "notificationService")
    public Mono<NotificationResult> sendWelcomeNotification(
            String correlationId, User user, NotificationPreferences prefs) {
        return Mono.fromCallable(() -> {
                    log.info(
                            "[{}] Sending welcome notification to user: {} via {}",
                            correlationId,
                            user.username(),
                            prefs.channel());
                    // Simulate a 30% chance of failure to test retry and circuit breaker logic.
                    if (random.nextDouble() < 0.3)
                        throw new RuntimeException("Notification service temporarily unavailable");
                    // On success, return a result with a unique message ID.
                    return NotificationResult.ok(java.util.UUID.randomUUID().toString());
                })
                // Simulate network latency with a random delay.
                .delayElement(java.time.Duration.ofMillis(random.nextInt(1000) + 500))
                // Offload the execution to a dedicated scheduler to avoid blocking the calling thread.
                .subscribeOn(scheduler)
                .doOnSuccess(result -> log.info("Notification sent successfully: {}", result))
                .doOnError(error -> log.error("Failed to send notification", error));
    }

    /**
     * Sends a status update notification. Also protected by resilience patterns.
     *
     * @param correlationId for distributed tracing
     * @param user the user to notify
     * @param status the new status to report
     * @param metadata additional data for the notification
     * @return a Mono emitting a success or failure result
     */
    @Override
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackSendStatus")
    @Retry(name = "notificationService") // Uses the same retry configuration as the welcome notification.
    @Bulkhead(name = "notificationService")
    public Mono<NotificationResult> sendStatusUpdate(
            String correlationId, User user, String status, java.util.Map<String, Object> metadata) {
        return Mono.fromCallable(() -> {
                    log.info(
                            "[{}] Sending status update {} notification to user: {}",
                            correlationId,
                            status,
                            user.username());
                    // Simulate a 20% chance of failure.
                    if (random.nextDouble() < 0.2) throw new RuntimeException("Notification service error");
                    return NotificationResult.ok(java.util.UUID.randomUUID().toString());
                })
                // Simulate network latency.
                .delayElement(java.time.Duration.ofMillis(random.nextInt(800) + 200))
                // Offload to the dedicated scheduler.
                .subscribeOn(scheduler);
    }

    /**
     * Fallback method for {@link #sendWelcomeNotification}.
     *
     * <p>This method is invoked by the CircuitBreaker when it is open or when a call fails after
     * all retries have been exhausted. It logs the failure and returns a 'queued' status,
     * indicating that the notification could be processed later by a background job.
     *
     * @param user The original user parameter.
     * @param prefs The original preferences parameter.
     * @param ex The exception that triggered the fallback.
     * @return A Mono with a 'failed' NotificationResult, indicating graceful degradation.
     */
    public Mono<NotificationResult> fallbackSendWelcome(
            String correlationId, User user, NotificationPreferences prefs, Exception ex) {
        log.warn("Using fallback for welcome notification for user: {}, error: {}", user.username(), ex.getMessage());
        return Mono.just(NotificationResult.failed("queued"));
    }

    /**
     * Fallback method for {@link #sendStatusUpdate}.
     * @return A Mono with a 'failed' NotificationResult.
     */
    public Mono<NotificationResult> fallbackSendStatus(
            String correlationId, User user, String status, java.util.Map<String, Object> metadata, Exception ex) {
        log.warn("Using fallback for status notification for user: {}, error: {}", user.username(), ex.getMessage());
        return Mono.just(NotificationResult.failed("queued"));
    }
}
