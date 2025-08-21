package com.resilient.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
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
@Profile("!local & !dev") // Only active in non-local/non-dev profiles
public class ReactiveKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaConsumer.class);

    /**
     * Kafka listener method for consuming messages from 'demo-topic'.
     *
     * <p>Processes each record reactively off the Kafka listener thread to avoid blocking.
     *
     * @param record the consumed Kafka record
     */
    @KafkaListener(topics = "demo-topic", groupId = "demo-group")
    public void listen(ConsumerRecord<String, String> record) {
        // Process record reactively off the Kafka listener thread
        Mono.fromCallable(() -> processRecord(record))
                .subscribeOn(Schedulers.boundedElastic()) // Run on elastic thread pool
                .doOnError(e -> logger.error("Kafka consume error", e)) // Log errors
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
}
