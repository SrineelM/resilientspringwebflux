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
 * <p>Uses Resilience4j annotations for circuit breaking, retry, and bulkhead isolation. Simulates a
 * notification service with random failures and delays for demonstration.
 */
@Service
public class NotificationAdapter implements UserNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdapter.class);
    private final Random random = new Random();
    private final Scheduler scheduler;

    /**
     * Constructs a NotificationAdapter with a specific Reactor scheduler.
     *
     * @param scheduler the scheduler to use for async notification tasks
     */
    public NotificationAdapter(@Qualifier("notificationScheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sends a welcome notification to the given user.
     *
     * <p>This method is protected by circuit breaker, retry, and bulkhead patterns. It simulates
     * random failures and artificial latency for demonstration purposes.
     *
     * @param user the user to notify
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
                    if (random.nextDouble() < 0.3)
                        throw new RuntimeException("Notification service temporarily unavailable");
                    return NotificationResult.ok(java.util.UUID.randomUUID().toString());
                })
                .delayElement(java.time.Duration.ofMillis(random.nextInt(1000) + 500))
                .subscribeOn(scheduler)
                .doOnSuccess(result -> log.info("Notification sent successfully: {}", result))
                .doOnError(error -> log.error("Failed to send notification", error));
    }

    @Override
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackSendStatus")
    @Retry(name = "notificationService")
    @Bulkhead(name = "notificationService")
    public Mono<NotificationResult> sendStatusUpdate(
            String correlationId, User user, String status, java.util.Map<String, Object> metadata) {
        return Mono.fromCallable(() -> {
                    log.info(
                            "[{}] Sending status update {} notification to user: {}",
                            correlationId,
                            status,
                            user.username());
                    if (random.nextDouble() < 0.2) throw new RuntimeException("Notification service error");
                    return NotificationResult.ok(java.util.UUID.randomUUID().toString());
                })
                .delayElement(java.time.Duration.ofMillis(random.nextInt(800) + 200))
                .subscribeOn(scheduler);
    }

    public Mono<NotificationResult> fallbackSendWelcome(
            String correlationId, User user, NotificationPreferences prefs, Exception ex) {
        log.warn("Using fallback for welcome notification for user: {}, error: {}", user.username(), ex.getMessage());
        return Mono.just(NotificationResult.failed("queued"));
    }

    public Mono<NotificationResult> fallbackSendStatus(
            String correlationId, User user, String status, java.util.Map<String, Object> metadata, Exception ex) {
        log.warn("Using fallback for status notification for user: {}, error: {}", user.username(), ex.getMessage());
        return Mono.just(NotificationResult.failed("queued"));
    }
}
