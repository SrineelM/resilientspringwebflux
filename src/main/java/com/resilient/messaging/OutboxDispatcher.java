package com.resilient.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Periodic poller that reads NEW outbox rows, marks them IN_PROGRESS, dispatches via messaging
 * adapters (Kafka &amp; ActiveMQ if available / profile enabled) then marks PUBLISHED or FAILED.
 * Enabled only outside local/dev to keep lightweight for developers.
 *
 * <h2>Backpressure Strategy</h2>
 * <ul>
 *   <li><strong>flatMap concurrency cap</strong>: {@code flatMap(this::processEvent, dispatchConcurrency)}
 *       limits how many outbox events are processed simultaneously. At most
 *       {@code dispatchConcurrency} (default 4, configurable) events are in-flight at once.
 *       This prevents unbounded goroutine/memory growth when the broker is slow.</li>
 *   <li><strong>Poll guard</strong>: an {@link AtomicBoolean} lock ({@code polling}) prevents
 *       overlapping poll cycles. If the scheduler fires while a previous poll cycle is still
 *       running, the new invocation is skipped with a warning. Without this guard, long-running
 *       batch dispatches could stack up and amplify load on the database and message broker.</li>
 *   <li><strong>Batch size</strong>: {@code LIMIT :batchSize} in the SQL query bounds how many
 *       rows are fetched per cycle, providing natural upper-bound on per-cycle work.</li>
 * </ul>
 */
@Component
@EnableScheduling
@Profile("!local & !dev")
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final DatabaseClient db;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReactiveKafkaProducer kafkaProducer;
    private final ActiveMqProducerPort activeMqProducer;

    /**
     * Maximum number of outbox events dispatched concurrently per poll cycle.
     *
     * <p><strong>Backpressure note</strong>: this controls the {@code flatMap} concurrency
     * parameter. Higher values increase throughput but also increase broker and DB load.
     * Default: 4 concurrent dispatches.
     */
    @Value("${outbox.dispatch.concurrency:4}")
    private int dispatchConcurrency;

    @Value("${outbox.dispatch.batchSize:25}")
    private int batchSize;

    @Value("${outbox.dispatch.enableKafka:true}")
    private boolean enableKafka;

    @Value("${outbox.dispatch.enableActiveMq:true}")
    private boolean enableActiveMq;

    @Value("${outbox.dispatch.kafka.topic:outbox.events}")
    private String kafkaTopic;

    @Value("${outbox.dispatch.activemq.destination:outbox.events}")
    private String activeMqDestination;

    /**
     * Guard flag that prevents overlapping poll cycles when a previous poll is still running.
     *
     * <p><strong>Backpressure note</strong>: without this guard, the {@code @Scheduled} timer
     * could fire a new {@link #poll()} while the previous reactive pipeline is still running,
     * stacking up duplicate dispatches and amplifying pressure on the DB and broker.
     */
    private final AtomicBoolean polling = new AtomicBoolean(false);

    public OutboxDispatcher(
            DatabaseClient db, ReactiveKafkaProducer kafkaProducer, ActiveMqProducerPort activeMqProducer) {
        this.db = db;
        this.kafkaProducer = kafkaProducer;
        this.activeMqProducer = activeMqProducer;
    }

    /**
     * Scheduled poll entry point. Skips the cycle if a previous one is still running.
     *
     * <p>Uses {@link AtomicBoolean#compareAndSet} to ensure only one poll cycle runs at a time.
     * The flag is always reset in {@code doFinally} so a failed/cancelled cycle releases the lock.
     */
    @Scheduled(fixedDelayString = "${outbox.dispatch.interval.ms:5000}")
    public void poll() {
        // [BACKPRESSURE - FIX] compareAndSet ensures only one poll runs at a time.
        // If a previous cycle is still processing, skip this invocation instead of stacking.
        if (!polling.compareAndSet(false, true)) {
            log.warn("Outbox poll skipped — previous cycle still running");
            return;
        }

        dispatchBatch()
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .doOnError(err -> log.error("Outbox dispatch cycle error: {}", err.toString()))
                // Always release the lock, even on error or cancellation.
                .doFinally(signal -> polling.set(false))
                .subscribe();
    }

    /**
     * Fetches a batch of new outbox events and dispatches them with bounded concurrency.
     *
     * <p><strong>Backpressure</strong>: {@code flatMap(fn, dispatchConcurrency)} caps the number
     * of simultaneously in-flight dispatches. When the concurrency limit is reached, upstream
     * record fetching is paused (Reactive Streams backpressure).
     *
     * @return a Mono that completes when the entire batch has been dispatched (or failed/DLQ'd).
     */
    private Mono<Void> dispatchBatch() {
        return fetchNewEvents()
                // [BACKPRESSURE] dispatchConcurrency is now configurable (was hardcoded to 4).
                // It limits parallel in-flight dispatches to prevent DB and broker overload.
                .flatMap(this::processEvent, dispatchConcurrency)
                .then();
    }

    private Flux<OutboxRow> fetchNewEvents() {
        return db.sql(
                        "UPDATE message_outbox SET status='IN_PROGRESS' WHERE id IN (SELECT id FROM message_outbox WHERE status='NEW' ORDER BY id LIMIT :lim) RETURNING id, aggregate_type, aggregate_id, event_type, payload, headers")
                .bind("lim", batchSize)
                .map((row, meta) -> new OutboxRow(
                        row.get("id", Long.class),
                        row.get("aggregate_type", String.class),
                        row.get("aggregate_id", String.class),
                        row.get("event_type", String.class),
                        row.get("payload", String.class),
                        row.get("headers", String.class)))
                .all();
    }

    @CircuitBreaker(name = "outboxPublish", fallbackMethod = "circuitBreakerFallback")
    private Mono<Void> processEvent(OutboxRow row) {
        Map<String, String> headers = parseHeaders(row.headers());
        String correlationId =
                headers.getOrDefault("correlationId", UUID.randomUUID().toString());
        headers.put("correlationId", correlationId);
        headers = com.resilient.messaging.TracingHeaderUtil.ensureTracing(headers);
        Mono<Void> publishMono = Mono.empty();
        if (enableKafka) {
            publishMono = publishMono.then(kafkaProducer.sendWithHeaders(kafkaTopic, row.payload(), headers));
        }
        if (enableActiveMq) {
            publishMono = publishMono.then(activeMqProducer.sendMessage(activeMqDestination, row.payload(), headers));
        }
        // Basic retry with exponential backoff (manual simple approach)
        return publishMono
                .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofMillis(200))
                        .maxBackoff(Duration.ofSeconds(2)))
                .then(markPublished(row.id()))
                .doOnSuccess(v -> log.debug("Outbox published id={} correlationId={}", row.id(), correlationId))
                .onErrorResume(ex -> markFailed(row.id(), ex));
    }

    // Fallback: mark failed and continue
    @SuppressWarnings("unused")
    private Mono<Void> circuitBreakerFallback(OutboxRow row, Throwable ex) {
        return markFailed(row.id(), ex);
    }

    private Mono<Void> markPublished(Long id) {
        return db.sql("UPDATE message_outbox SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP WHERE id=:id")
                .bind("id", id)
                .then();
    }

    private Mono<Void> markFailed(Long id, Throwable ex) {
        log.warn("Outbox publish failed id={} reason={}", id, ex.getMessage());
        return db.sql("UPDATE message_outbox SET status='FAILED' WHERE id=:id")
                .bind("id", id)
                .then();
    }

    private Map<String, String> parseHeaders(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return mapper.readValue(
                    json, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception e) {
            log.warn("Failed to parse outbox headers json, returning empty: {}", e.getMessage());
            return Map.of();
        }
    }

    private record OutboxRow(
            Long id, String aggregateType, String aggregateId, String eventType, String payload, String headers) {}
}
