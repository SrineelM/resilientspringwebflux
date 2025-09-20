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


/**
 * Service for auditing user actions and events in a reactive, observable, and metrics-driven way.
 * <p>
 * This class implements the UserAuditPort and provides both single and batch audit operations.
 * It demonstrates best practices for:
 * <ul>
 *   <li>Reactive programming with Project Reactor (Mono/Flux)</li>
 *   <li>Observability with Micrometer metrics and tracing</li>
 *   <li>Timeouts, retries, and error handling</li>
 *   <li>Context propagation (e.g., correlationId via MDC)</li>
 *   <li>Batch processing with concurrency control</li>
 * </ul>
 * <p>
 * Metrics:
 * <ul>
 *   <li>audit.success.count: Number of successful audits</li>
 *   <li>audit.failure.count: Number of failed audits</li>
 *   <li>audit.latency.timer: Latency of audit operations</li>
 * </ul>
 * <p>
 * Configuration is injected via @Value for timeouts, batch size, concurrency, retries, and failure simulation.
 */
@Service
@Observed
public class AuditService implements UserAuditPort {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    // Micrometer metrics for observability
    private final Counter auditSuccessCounter;
    private final Counter auditFailureCounter;
    private final Timer auditLatencyTimer;
    // Used to simulate random failures and delays (for demo/testing)
    private final Random random;

    // Configurable properties (injected from application properties)
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

    /**
     * Constructs the AuditService and registers metrics with the provided MeterRegistry.
     *
     * @param registry Micrometer MeterRegistry for metrics
     */
    public AuditService(MeterRegistry registry) {
        this.random = new Random();
        this.auditSuccessCounter = registry.counter("audit.success.count");
        this.auditFailureCounter = registry.counter("audit.failure.count");
        this.auditLatencyTimer = registry.timer("audit.latency.timer");
    }


    /**
     * Audits a single user action.
     * <p>
     * This method:
     * <ul>
     *   <li>Validates input parameters</li>
     *   <li>Propagates correlationId via MDC for logging</li>
     *   <li>Performs the audit operation with timeout and error handling</li>
     *   <li>Runs on a boundedElastic scheduler for blocking compatibility</li>
     * </ul>
     *
     * @param correlationId Correlation ID for tracing/logging
     * @param action        Action being audited
     * @param user          User performing the action
     * @param context       Additional context for the audit
     * @return Mono emitting the audit result
     */
    @Override
    public Mono<AuditResult> auditUserAction(
        String correlationId, String action, User user, Map<String, Object> context) {
    return Mono.deferContextual(contextView -> {
            // Propagate correlationId to MDC for log correlation
            try (var mdcCloseable = MDC.putCloseable("correlationId", correlationId)) {
            log.info(
                "Processing audit request: correlationId={}, action={}, userId={}",
                correlationId,
                action,
                user.id());

            // Validate required inputs
            validateAuditInputs(correlationId, action, user);

            // Perform the core audit operation with timeout and error handling
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
        // Use boundedElastic for compatibility with blocking MDC/logging
        .subscribeOn(Schedulers.boundedElastic());
    }


    /**
     * Audits a batch of user actions/events with concurrency and error handling.
     * <p>
     * This method:
     * <ul>
     *   <li>Validates and truncates the batch if needed</li>
     *   <li>Processes each event in parallel (up to batchConcurrency)</li>
     *   <li>Aggregates results and logs batch summary</li>
     *   <li>Applies a longer timeout for batch operations</li>
     * </ul>
     *
     * @param events List of audit events to process
     * @return Mono emitting the list of audit results
     */
    @Override
    public Mono<List<AuditResult>> auditBatchActions(List<AuditEvent> events) {
    if (events == null || events.isEmpty()) {
        log.info("Empty batch audit request received");
        return Mono.just(List.of());
    }

    // Truncate batch if it exceeds maxBatchSize
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
        // Allow more time for batch operations
        .timeout(Duration.ofSeconds(auditTimeoutSeconds * 2));
    }


    /**
     * Core audit operation with retry logic, metrics, and simulated failures/delays.
     * <p>
     * This method demonstrates:
     * <ul>
     *   <li>Reactive error handling and retry with backoff</li>
     *   <li>Metrics collection for latency and success/failure counts</li>
     *   <li>Simulated failures and delays for demonstration/testing</li>
     *   <li>Where to implement real audit persistence/integration logic</li>
     * </ul>
     *
     * @param correlationId Correlation ID for tracing/logging
     * @param action        Action being audited
     * @param user          User performing the action
     * @param context       Additional context for the audit
     * @return Mono emitting the audit result
     */
    private Mono<AuditResult> performAuditOperation(
            String correlationId, String action, User user, Map<String, Object> context) {
        // Start a timer sample for latency measurement
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

                    // Simulate processing with random delay (remove in production)
                    Duration delay = Duration.ofMillis(random.nextInt(200) + 50);

                    // Generate a unique audit ID and result
                    String auditId = UUID.randomUUID().toString();
                    AuditResult result =
                            AuditResult.success(auditId, correlationId, "User action audited successfully");

                    // --- Place for real audit logic ---
                    // - Save to audit database
                    // - Send to external audit system
                    // - Write to audit log files
                    // - Publish to message queue
                    processAuditData(auditId, correlationId, action, user, context);

                    // Simulate async processing delay
                    return Mono.just(result).delayElement(delay);
                })
                .doOnNext(result -> {
                    // Stop timer and increment success counter
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
                    // Stop timer and increment failure counter
                    sample.stop(auditLatencyTimer);
                    auditFailureCounter.increment();
                    log.error("[{}] Audit failed for action '{}': {}", correlationId, action, error.getMessage());
                    return Mono.just(AuditResult.fallback("Audit service unavailable: " + error.getMessage()));
                });
    }


    /**
     * Process and persist audit data.
     * <p>
     * This is a placeholder for your actual audit logic, such as:
     * <ul>
     *   <li>Persisting to an audit database</li>
     *   <li>Sending to an external audit system</li>
     *   <li>Writing to audit log files</li>
     *   <li>Publishing to a message queue</li>
     * </ul>
     *
     * @param auditId       Unique audit record ID
     * @param correlationId Correlation ID for tracing/logging
     * @param action        Action being audited
     * @param user          User performing the action
     * @param context       Additional context for the audit
     */
    private void processAuditData(
            String auditId, String correlationId, String action, User user, Map<String, Object> context) {
        log.debug(
                "Processing audit data: auditId={}, action={}, userId={}, contextKeys={}",
                auditId,
                action,
                user.id(),
                context != null ? context.keySet() : "[]");
        // Implement your actual audit persistence/integration logic here
    }


    /**
     * Validates required audit input parameters.
     * Throws IllegalArgumentException if any required field is missing.
     *
     * @param correlationId Correlation ID (must not be null/empty)
     * @param action        Action (must not be null/empty)
     * @param user          User (must not be null, must have non-null ID)
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
     * Custom exception for audit-specific errors (used for retry logic).
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
     * Setter for batchConcurrency (for testing purposes).
     *
     * @param batchConcurrency New concurrency value
     */
    public void setBatchConcurrency(int batchConcurrency) {
        this.batchConcurrency = batchConcurrency;
    }
}
