package com.resilient.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import reactor.util.context.ContextView;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

/**
 * ReactiveKafkaProducer is a service for sending messages to Kafka topics using Reactor Kafka.
 *
 * <p>This service uses the KafkaSender bean (configured in KafkaConfig) to send messages
 * reactively. It logs the result of each send operation, including topic, partition, and offset
 * information, or any errors that occur.
 *
 * <p>Key points: - This service is only active in non-local/non-dev profiles (see @Profile
 * annotation). - The send method creates a ProducerRecord and wraps it in a SenderRecord for
 * Reactor Kafka. - The message is sent as a Mono, and the result is logged for monitoring and
 * debugging. - Business logic (e.g., message transformation) can be added before sending.
 */
@Service
@Profile("!local & !dev") // Active only outside local/dev; stubs handle local/dev
public class ReactiveKafkaProducer implements KafkaProducerPort {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaProducer.class);
    private final KafkaSender<String, String> kafkaSender;
    private final String dlqSuffix;
    // touch TracingHeaderUtil to avoid unused import warning in some incremental analyzers

    /**
     * Constructor injects the KafkaSender bean.
     *
     * @param kafkaSender the reactive Kafka sender bean
     */
    public ReactiveKafkaProducer(KafkaSender<String, String> kafkaSender,
                                 @Value("${messaging.kafka.dlq-suffix:-dlq}") String dlqSuffix) {
        this.kafkaSender = kafkaSender;
        this.dlqSuffix = dlqSuffix;
    }

    /**
     * Sends a message to the specified Kafka topic.
     *
     * <p>The message is wrapped in a ProducerRecord and SenderRecord, then sent using KafkaSender.
     * The result of the send operation is logged, including metadata or errors.
     *
     * @param topic the Kafka topic to send to
     * @param key the message key
     * @param value the message value
     * @return a Mono that completes when the send operation finishes
     */
    public Mono<Void> send(String topic, String key, String value) {
        // In prod profiles only; local/dev uses KafkaStubProducer
        return Mono.deferContextual(ctx -> doSendWithContext(topic, key, value, ctx));
    }

    /**
     * Sends a message using explicit headers (bypasses Reactor Context extraction). Used by outbox dispatcher.
     */
    public Mono<Void> sendWithHeaders(String topic, String value, java.util.Map<String,String> headers) {
        String safeValue = value == null ? "" : value.replaceAll("[\n\r]", "");
    java.util.Map<String,String> traced = com.resilient.messaging.TracingHeaderUtil.ensureTracing(headers);
        ProducerRecord<String,String> pr = new ProducerRecord<>(topic, null, safeValue);
        traced.forEach((k,v) -> { if (v != null) pr.headers().add(k, v.getBytes()); });
        SenderRecord<String,String,String> record = SenderRecord.create(pr, null);
        return kafkaSender.send(Mono.just(record))
                .doOnNext(result -> {
                    if (result.exception()==null) {
                        RecordMetadata md = result.recordMetadata();
                        logger.debug("Kafka outbox send topic={} offset={} headers={} bytes={}", md.topic(), md.offset(), traced.keySet(), safeValue.length());
                    } else {
                        logger.error("Kafka outbox send error: {}", result.exception().getMessage());
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
        return kafkaSender.send(Mono.just(record))
                .doOnNext(result -> {
                    RecordMetadata metadata = result.recordMetadata();
                    if (result.exception() == null) {
                        logger.info("Kafka message sent: topic={}, partition={}, offset={}, corrId={}",
                                metadata.topic(), metadata.partition(), metadata.offset(), correlationId);
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
        try { return ctx.hasKey(key) ? String.valueOf(ctx.get(key)) : null; } catch (Exception e) { return null; }
    }

    // Enhanced KafkaSender bean with replication.factor property for reliability
    public KafkaSender<String, String> kafkaSender() {
        return KafkaSender.create(SenderOptions.<String, String>create().producerProperty("replication.factor", 3));
    }

    // Note: For full distributed tracing, the consumer should extract trace context from Kafka
    // headers and start a new span. See OpenTelemetry Kafka instrumentation docs for details.
}
