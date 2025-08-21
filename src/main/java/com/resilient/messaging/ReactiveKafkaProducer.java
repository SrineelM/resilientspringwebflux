package com.resilient.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
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
@Profile("!local & !dev") // Only active in non-local/non-dev profiles
public class ReactiveKafkaProducer implements KafkaProducerPort {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaProducer.class);
    private final KafkaSender<String, String> kafkaSender;

    /**
     * Constructor injects the KafkaSender bean.
     *
     * @param kafkaSender the reactive Kafka sender bean
     */
    public ReactiveKafkaProducer(KafkaSender<String, String> kafkaSender) {
        this.kafkaSender = kafkaSender;
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
        String safeValue = value.replaceAll("[\n\r]", ""); // Remove newlines for safe logging
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, key, safeValue);
        SenderRecord<String, String, String> record = SenderRecord.create(producerRecord, key);
        return kafkaSender
                .send(Mono.just(record))
                .doOnNext(result -> {
                    RecordMetadata metadata = result.recordMetadata();
                    if (result.exception() == null) {
                        logger.info(
                                "Kafka message sent: topic={}, partition={}, offset={}",
                                metadata.topic(),
                                metadata.partition(),
                                metadata.offset());
                    } else {
                        logger.error("Kafka send error: {}", result.exception().getMessage());
                    }
                })
                .then();
    }

    // Enhanced KafkaSender bean with replication.factor property for reliability
    public KafkaSender<String, String> kafkaSender() {
        return KafkaSender.create(SenderOptions.<String, String>create().producerProperty("replication.factor", 3));
    }

    // Note: For full distributed tracing, the consumer should extract trace context from Kafka
    // headers and start a new span. See OpenTelemetry Kafka instrumentation docs for details.
}
