package com.resilient.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.apache.kafka.clients.producer.ProducerRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * ReactiveKafkaConsumer consumes Kafka messages reactively using Spring Kafka and Project Reactor.
 *
 * <p>This service listens to messages from the 'demo-topic' Kafka topic and processes them off the
 * Kafka listener thread using a bounded elastic scheduler. This allows for non-blocking, scalable
 * message processing, even if business logic is blocking or CPU-intensive.
 *
 * <p>Key points: - Uses @KafkaListener to subscribe to the topic and group. - Processes each
 * message reactively with Mono and Schedulers.boundedElastic(). - Handles errors gracefully and
 * logs them. - Add business logic in processRecord() for custom processing.
 */
@Service
@Profile("!local & !dev") // Active only outside local/dev; stub (if any) would run in local
public class ReactiveKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaConsumer.class);
    private final KafkaSender<String,String> kafkaSender;
    private final String dlqSuffix;

    public ReactiveKafkaConsumer(KafkaSender<String,String> kafkaSender,
                                 @Value("${messaging.kafka.dlq-suffix:-dlq}") String dlqSuffix) {
        this.kafkaSender = kafkaSender;
        this.dlqSuffix = dlqSuffix;
    }

    /**
     * Kafka listener method for consuming messages from 'demo-topic'.
     *
     * <p>Processes each record reactively off the Kafka listener thread to avoid blocking.
     *
     * @param record the consumed Kafka record
     */
    @KafkaListener(topics = "${messaging.kafka.consumer.topic:demo-topic}", groupId = "${messaging.kafka.consumer.group:demo-group}")
    public void listen(ConsumerRecord<String, String> record) {
        // Process record reactively off the Kafka listener thread
    Mono.fromCallable(() -> processRecord(record))
                .subscribeOn(Schedulers.boundedElastic()) // Run on elastic thread pool
                .doOnError(e -> logger.error("Kafka consume error", e)) // Log errors
        .onErrorResume(e -> sendToDlq(record, e).then(Mono.empty()))
                .subscribe();
    }

    /**
     * Processes the consumed Kafka record.
     *
     * <p>Add business logic here for custom processing, such as saving to DB or calling services.
     *
     * @param record the consumed Kafka record
     * @return null (void)
     */
    private Void processRecord(ConsumerRecord<String, String> record) {
        logger.info(
                "Received message: key={}, value={}, partition={}, offset={}",
                record.key(),
                record.value(),
                record.partition(),
                record.offset());
        // Add business logic here
        return null;
    }

    private Mono<Void> sendToDlq(ConsumerRecord<String,String> original, Throwable ex) {
        try {
            String dlqTopic = original.topic() + dlqSuffix;
            ProducerRecord<String,String> pr = new ProducerRecord<>(dlqTopic, original.key(), original.value());
            pr.headers().add("x-exception", ex.getClass().getName().getBytes());
            pr.headers().add("x-exception-message", ex.getMessage() == null ? new byte[0] : ex.getMessage().getBytes());
            pr.headers().add("x-original-partition", String.valueOf(original.partition()).getBytes());
            pr.headers().add("x-original-offset", String.valueOf(original.offset()).getBytes());
            return kafkaSender.send(Mono.just(SenderRecord.create(pr, original.key()))).then();
        } catch (Exception secondary) {
            logger.error("Failed to route message to DLQ after consumption failure: {}", secondary.getMessage());
            return Mono.empty();
        }
    }
}
