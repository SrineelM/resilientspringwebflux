package com.resilient.service.external;

import com.resilient.model.User;
import com.resilient.ports.UserAuditPort;
import com.resilient.ports.dto.AuditEvent;
import com.resilient.ports.dto.AuditResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
@Observed
public class AuditService implements UserAuditPort {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final Counter auditSuccessCounter;
    private final Counter auditFailureCounter;
    private final Timer auditLatencyTimer;
    private final Random random;

    @Value("${audit.timeout.seconds:10}")
    private int auditTimeoutSeconds;

    @Value("${audit.batch.max-size:100}")
    private int maxBatchSize;

    @Value("${audit.batch.concurrency:10}")
    private int batchConcurrency;

    @Value("${audit.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${audit.failure.rate:0.15}")
    private double failureRate;

    public AuditService(MeterRegistry registry) {
        this.random = new Random();
        this.auditSuccessCounter = registry.counter("audit.success.count");
        this.auditFailureCounter = registry.counter("audit.failure.count");
        this.auditLatencyTimer = registry.timer("audit.latency.timer");
    }

    @Override
    public Mono<AuditResult> auditUserAction(
            String correlationId, String action, User user, Map<String, Object> context) {
        return Mono.deferContextual(contextView -> {
                    try (var mdcCloseable = MDC.putCloseable("correlationId", correlationId)) {
                        log.info(
                                "Processing audit request: correlationId={}, action={}, userId={}",
                                correlationId,
                                action,
                                user.id());

                        // Validate inputs
                        validateAuditInputs(correlationId, action, user);

                        return performAuditOperation(correlationId, action, user, context)
                                .timeout(Duration.ofSeconds(auditTimeoutSeconds))
                                .onErrorResume(throwable -> {
                                    log.error(
                                            "Audit processing failed for correlationId={}: {}",
                                            correlationId,
                                            throwable.getMessage());
                                    return Mono.just(AuditResult.failure(
                                            correlationId, "Audit service error: " + throwable.getMessage()));
                                });
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<AuditResult>> auditBatchActions(List<AuditEvent> events) {
        if (events == null || events.isEmpty()) {
            log.info("Empty batch audit request received");
            return Mono.just(List.of());
        }

        if (events.size() > maxBatchSize) {
            log.warn(
                    "Batch size {} exceeds maximum {}. Processing first {} events",
                    events.size(),
                    maxBatchSize,
                    maxBatchSize);
            events = events.subList(0, maxBatchSize);
        }

        log.info("Processing batch audit with {} events", events.size());

        return Flux.fromIterable(events)
                .flatMap(
                        event -> auditUserAction(event.correlationId(), event.action(), event.user(), event.context())
                                .onErrorResume(error -> {
                                    log.error(
                                            "Failed to audit event: correlationId={}, error={}",
                                            event.correlationId(),
                                            error.getMessage());
                                    return Mono.just(AuditResult.failure(
                                            event.correlationId(), "Batch audit failed: " + error.getMessage()));
                                }),
                        batchConcurrency)
                .collectList()
                .doOnSuccess(results -> {
                    long successCount = results.stream()
                            .mapToLong(r -> r.isSuccess() ? 1 : 0)
                            .sum();
                    long failureCount = results.size() - successCount;
                    log.info(
                            "Batch audit completed: total={}, success={}, failures={}",
                            results.size(),
                            successCount,
                            failureCount);
                })
                .timeout(Duration.ofSeconds(auditTimeoutSeconds * 2)); // Longer timeout for batch
    }

    /**
     * Core audit operation with retry logic and metrics
     */
    private Mono<AuditResult> performAuditOperation(
            String correlationId, String action, User user, Map<String, Object> context) {
        Timer.Sample sample = Timer.start();

        return Mono.defer(() -> {
                    log.info(
                            "[{}] Auditing action '{}' for user: {} with context keys: {}",
                            correlationId,
                            action,
                            user.username(),
                            context != null ? context.keySet() : "[]");

                    // Simulate random failures for demonstration (remove in production)
                    if (random.nextDouble() < failureRate) {
                        return Mono.error(new AuditServiceException("Audit service temporarily unavailable"));
                    }

                    // Simulate processing with random delay
                    Duration delay = Duration.ofMillis(random.nextInt(200) + 50);

                    // Generate audit result
                    String auditId = UUID.randomUUID().toString();
                    AuditResult result =
                            AuditResult.success(auditId, correlationId, "User action audited successfully");

                    // Here you would implement actual audit logic:
                    // - Save to audit database
                    // - Send to external audit system
                    // - Write to audit log files
                    // - Publish to message queue
                    processAuditData(auditId, correlationId, action, user, context);

                    return Mono.just(result).delayElement(delay);
                })
                .doOnNext(result -> {
                    sample.stop(auditLatencyTimer);
                    auditSuccessCounter.increment();
                    log.debug("[{}] Audit completed successfully: {}", correlationId, result.id());
                })
                .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof AuditServiceException)
                        .doBeforeRetry(signal -> log.warn(
                                "[{}] Retrying audit for action '{}' - attempt {}",
                                correlationId,
                                action,
                                signal.totalRetries() + 1)))
                .onErrorResume(error -> {
                    sample.stop(auditLatencyTimer);
                    auditFailureCounter.increment();
                    log.error("[{}] Audit failed for action '{}': {}", correlationId, action, error.getMessage());
                    return Mono.just(AuditResult.fallback("Audit service unavailable: " + error.getMessage()));
                });
    }

    /**
     * Process and persist audit data - implement your actual audit logic here
     */
    private void processAuditData(
            String auditId, String correlationId, String action, User user, Map<String, Object> context) {
        log.debug(
                "Processing audit data: auditId={}, action={}, userId={}, contextKeys={}",
                auditId,
                action,
                user.id(),
                context != null ? context.keySet() : "[]");

        // Example implementations:
        // 1. Database persistence
        // auditRepository.save(createAuditRecord(auditId, correlationId, action, user, context));

        // 2. Message queue publishing
        // messagePublisher.publish(createAuditMessage(auditId, correlationId, action, user, context));

        // 3. External audit service call
        // externalAuditClient.sendAuditEvent(auditId, correlationId, action, user, context);

        // 4. Log file writing
        // auditLogger.logAuditEvent(auditId, correlationId, action, user, context);
    }

    /**
     * Validate audit inputs
     */
    private void validateAuditInputs(String correlationId, String action, User user) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or empty");
        }
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.id() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
    }

    /**
     * Custom exception for audit-specific errors
     */
    public static class AuditServiceException extends RuntimeException {
        public AuditServiceException(String message) {
            super(message);
        }

        public AuditServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Setter for batchConcurrency (for testing purposes)
     */
    public void setBatchConcurrency(int batchConcurrency) {
        this.batchConcurrency = batchConcurrency;
    }
}
