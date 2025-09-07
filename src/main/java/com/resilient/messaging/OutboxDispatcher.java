package com.resilient.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
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
 * adapters (Kafka & ActiveMQ if available / profile enabled) then marks PUBLISHED or FAILED.
 * Enabled only outside local/dev to keep lightweight for developers.
 */
@Component
@EnableScheduling
@Profile("!local & !dev")
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final DatabaseClient db;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReactiveKafkaProducer kafkaProducer; // optional via profile
    private final ActiveMqProducerPort activeMqProducer; // interface abstraction

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

    public OutboxDispatcher(
            DatabaseClient db, ReactiveKafkaProducer kafkaProducer, ActiveMqProducerPort activeMqProducer) {
        this.db = db;
        this.kafkaProducer = kafkaProducer;
        this.activeMqProducer = activeMqProducer;
    }

    @Scheduled(fixedDelayString = "${outbox.dispatch.interval.ms:5000}")
    public void poll() {
        dispatchBatch()
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .doOnError(err -> log.error("Outbox dispatch cycle error: {}", err.toString()))
                .subscribe();
    }

    private Mono<Void> dispatchBatch() {
        return fetchNewEvents()
                .flatMap(this::processEvent, 4) // limited concurrency
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
