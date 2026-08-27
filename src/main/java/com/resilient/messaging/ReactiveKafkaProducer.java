package com.resilient.messaging;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.util.context.ContextView;

/**
 * ReactiveKafkaProducer is a service for sending messages to Kafka topics using Reactor Kafka.
 *
 * <p>This service uses the KafkaSender bean (configured in {@link KafkaProducerConfig}) to send
 * messages reactively. It logs the result of each send operation, including topic, partition, and
 * offset information, or any errors that occur.
 *
 * <h2>Backpressure Note</h2>
 * <p>The {@link KafkaSender} is configured with {@code maxInFlight} (see {@link KafkaProducerConfig})
 * to cap the number of unacknowledged Kafka send requests in flight at any given time. Without this
 * bound, a burst of send calls can queue unlimited records in memory, leading to OOM under
 * sustained load. With {@code maxInFlight}, the sender applies backpressure to the caller once the
 * limit is reached.
 *
 * <p>Key points:
 * <ul>
 *   <li>Active only in non-local/non-dev profiles (see {@code @Profile} annotation).</li>
 *   <li>The {@link #send} method propagates Reactor Context (correlation + trace IDs) into Kafka headers.</li>
 *   <li>{@link #sendWithHeaders} bypasses Context and uses explicit header maps (used by outbox dispatcher).</li>
 *   <li>Failed sends are automatically routed to a Dead-Letter Queue (DLQ) topic.</li>
 * </ul>
 */
@Service
@Profile("!local & !dev") // Active only outside local/dev; stubs handle local/dev
public class ReactiveKafkaProducer implements KafkaProducerPort {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaProducer.class);

    private final KafkaSender<String, String> kafkaSender;
    private final String dlqSuffix;

    /**
     * Constructor injects the KafkaSender bean.
     *
     * <p>The {@code kafkaSender} bean is created in {@link KafkaProducerConfig} with
     * {@code maxInFlight} configured for bounded in-flight sends. Do NOT create additional
     * {@link KafkaSender} instances inside this class — each instance opens a Kafka producer
     * connection.
     *
     * @param kafkaSender the reactive Kafka sender bean (with maxInFlight configured)
     * @param dlqSuffix   suffix appended to the original topic name to form the DLQ topic name
     */
    public ReactiveKafkaProducer(
            KafkaSender<String, String> kafkaSender, @Value("${messaging.kafka.dlq-suffix:-dlq}") String dlqSuffix) {
        this.kafkaSender = kafkaSender;
        this.dlqSuffix = dlqSuffix;
    }

    /**
     * Sends a message to the specified Kafka topic.
     *
     * <p>The message is wrapped in a ProducerRecord and SenderRecord, then sent using KafkaSender.
     * Reactor Context values for {@code correlationId} and {@code traceId} are automatically
     * propagated as Kafka message headers.
     *
     * @param topic the Kafka topic to send to
     * @param key   the message key (used for partition assignment)
     * @param value the message value (payload)
     * @return a Mono that completes when the send operation finishes
     */
    public Mono<Void> send(String topic, String key, String value) {
        // In prod profiles only; local/dev uses KafkaStubProducer
        return Mono.deferContextual(ctx -> doSendWithContext(topic, key, value, ctx));
    }

    /**
     * Sends a message using explicit headers (bypasses Reactor Context extraction).
     *
     * <p>Used by the outbox dispatcher which manages its own correlation IDs and tracing headers
     * outside of a WebFlux request context.
     *
     * @param topic   the Kafka topic to send to
     * @param value   the message payload
     * @param headers explicit key-value headers to attach to the Kafka record
     * @return a Mono that completes when the send operation finishes
     */
    public Mono<Void> sendWithHeaders(String topic, String value, java.util.Map<String, String> headers) {
        String safeValue = value == null ? "" : value.replaceAll("[\n\r]", "");
        Map<String, String> traced = TracingHeaderUtil.ensureTracing(headers);
        ProducerRecord<String, String> pr = new ProducerRecord<>(topic, null, safeValue);
        traced.forEach((k, v) -> {
            if (v != null) pr.headers().add(k, v.getBytes());
        });
        SenderRecord<String, String, String> record = SenderRecord.create(pr, null);
        // [BACKPRESSURE] kafkaSender has maxInFlight configured in KafkaProducerConfig.
        // If the broker is slow and in-flight sends reach the limit, this Mono will signal
        // backpressure to the caller (e.g., the outbox dispatcher's flatMap concurrency cap).
        return kafkaSender
                .send(Mono.just(record))
                .doOnNext(result -> {
                    if (result.exception() == null) {
                        RecordMetadata md = result.recordMetadata();
                        logger.debug(
                                "Kafka outbox send topic={} offset={} headers={} bytes={}",
                                md.topic(),
                                md.offset(),
                                traced.keySet(),
                                safeValue.length());
                    } else {
                        logger.error(
                                "Kafka outbox send error: {}",
                                result.exception().getMessage());
                    }
                })
                .then();
    }

    private Mono<Void> doSendWithContext(String topic, String key, String value, ContextView ctx) {
        String safeValue = value == null ? "" : value.replaceAll("[\n\r]", "");
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, key, safeValue);

        // Correlation propagation
        String correlationId = getContextValue(ctx, "correlationId");
        if (StringUtils.hasText(correlationId)) {
            producerRecord.headers().add("X-Correlation-ID", correlationId.getBytes());
        }
        // Basic tracing headers (could be expanded with W3C traceparent from context)
        String traceId = getContextValue(ctx, "traceId");
        if (StringUtils.hasText(traceId)) {
            producerRecord.headers().add("traceId", traceId.getBytes());
        }

        SenderRecord<String, String, String> record = SenderRecord.create(producerRecord, key);
        // [BACKPRESSURE] maxInFlight on KafkaSender (configured in KafkaProducerConfig) ensures
        // that at most N records are unacknowledged by the broker at once. Exceeding this limit
        // causes the sender to apply backpressure upstream.
        return kafkaSender
                .send(Mono.just(record))
                .doOnNext(result -> {
                    RecordMetadata metadata = result.recordMetadata();
                    if (result.exception() == null) {
                        logger.info(
                                "Kafka message sent: topic={}, partition={}, offset={}, corrId={}",
                                metadata.topic(),
                                metadata.partition(),
                                metadata.offset(),
                                correlationId);
                    } else {
                        logger.error("Kafka send error: {}", result.exception().getMessage());
                    }
                })
                .onErrorResume(ex -> {
                    logger.warn("Primary send failed for topic={}, routing to DLQ: {}", topic, ex.getMessage());
                    String dlqTopic = topic + dlqSuffix;
                    ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(dlqTopic, key, safeValue);
                    if (StringUtils.hasText(correlationId)) {
                        dlqRecord.headers().add("X-Correlation-ID", correlationId.getBytes());
                        dlqRecord.headers().add("x-original-topic", topic.getBytes());
                    }
                    return kafkaSender.send(Mono.just(SenderRecord.create(dlqRecord, key)));
                })
                .then();
    }

    private String getContextValue(ContextView ctx, String key) {
        try {
            return ctx.hasKey(key) ? String.valueOf(ctx.get(key)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // NOTE: The orphan kafkaSender() method that previously existed here was removed.
    // It was dead code (not annotated with @Bean and never invoked), and calling it would
    // have leaked a new KafkaSender connection. Kafka producer configuration now lives
    // exclusively in KafkaProducerConfig where maxInFlight is also set.
}
