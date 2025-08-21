package com.resilient.service.external;

import com.resilient.model.User;
import com.resilient.ports.UserNotificationPort;
import com.resilient.ports.dto.NotificationPreferences;
import com.resilient.ports.dto.NotificationResult;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * NotificationService
 *
 * <p>Example of how to call a BLOCKING REST API from a NON-BLOCKING reactive method.
 *
 * <p>Key ideas: 1. Wrap the blocking call in Mono.fromCallable(...) 2. Shift execution to a
 * boundedElastic or custom Scheduler using subscribeOn(...) - Prevents blocking the Netty event
 * loop threads. 3. Use delayElement only for simulating latency in tests/demos (real HTTP calls
 * won't need it). 4. Use Resilience4j annotations for retries, timeouts, circuit breakers, and
 * bulkheads. 5. Gracefully handle errors via fallback methods.
 *
 * <p>In a real case, replace the simulated delay/failure logic with a real blocking call (e.g.,
 * RestTemplate) inside the Mono.fromCallable.
 */
@Service
public class NotificationService implements UserNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final Scheduler scheduler;
    private final Random random = new Random();

    // Could be injected as a bean if used in multiple services
    private final RestTemplate restTemplate = new RestTemplate();

    public NotificationService(@Qualifier("notificationScheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackSendWelcome")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    @Bulkhead(name = "notificationService")
    public Mono<NotificationResult> sendWelcomeNotification(
            String correlationId, User user, NotificationPreferences prefs) {
        return Mono.fromCallable(() -> {
                    log.info(
                            "[{}] Sending welcome notification to user: {} via {}",
                            correlationId,
                            user.username(),
                            prefs.channel());

                    // --- Example blocking REST call ---
                    // String response = restTemplate.postForObject(
                    //         "https://notification-service/api/v1/welcome",
                    //         Map.of("userId", user.id(), "channel", prefs.channel()),
                    //         String.class
                    // );
                    // log.debug("REST response: {}", response);

                    // --- Simulated random failure for demo ---
                    if (random.nextDouble() < 0.3) {
                        throw new RuntimeException("Notification service temporarily unavailable");
                    }

                    return NotificationResult.ok(java.util.UUID.randomUUID().toString());
                })
                // Non-blocking simulated delay (remove in production)
                .delayElement(Duration.ofMillis(random.nextInt(1000) + 500))
                // Offload blocking call to custom/boundedElastic scheduler
                .subscribeOn(scheduler)
                .doOnSuccess(r -> log.info("Notification sent: {}", r))
                .doOnError(e -> log.error("Failed to send notification for user: {}", user.username(), e));
    }

    @Override
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackSendStatus")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    @Bulkhead(name = "notificationService")
    public Mono<NotificationResult> sendStatusUpdate(
            String correlationId, User user, String status, Map<String, Object> metadata) {
        return Mono.fromCallable(() -> {
                    log.info("[{}] Sending status update {} to user: {}", correlationId, status, user.username());

                    // --- Example blocking REST call ---
                    // String response = restTemplate.postForObject(
                    //         "https://notification-service/api/v1/status",
                    //         Map.of("userId", user.id(), "status", status, "meta", metadata),
                    //         String.class
                    // );
                    // log.debug("REST response: {}", response);

                    // --- Simulated random failure for demo ---
                    if (random.nextDouble() < 0.3) {
                        throw new RuntimeException("Notification service temporarily unavailable");
                    }

                    return NotificationResult.ok(java.util.UUID.randomUUID().toString());
                })
                .delayElement(Duration.ofMillis(random.nextInt(1000) + 500))
                .subscribeOn(scheduler)
                .doOnSuccess(r -> log.info("Notification sent: {}", r))
                .doOnError(e -> log.error("Failed to send status update for user: {}", user.username(), e));
    }

    // --- Fallback methods ---
    public Mono<NotificationResult> fallbackSendWelcome(
            String correlationId, User user, NotificationPreferences prefs, Exception ex) {
        log.warn("Fallback for welcome notification to {}: {}", user.username(), ex.getMessage());
        return Mono.just(NotificationResult.failed("queued"));
    }

    public Mono<NotificationResult> fallbackSendStatus(
            String correlationId, User user, String status, Map<String, Object> metadata, Exception ex) {
        log.warn("Fallback for status notification to {}: {}", user.username(), ex.getMessage());
        return Mono.just(NotificationResult.failed("queued"));
    }
}
