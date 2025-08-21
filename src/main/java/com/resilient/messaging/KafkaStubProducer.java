package com.resilient.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Stub Kafka producer for local/dev profile. Simulates sending messages without connecting to
 * Kafka.
 */
@Component
@Profile({"local", "dev"})
public class KafkaStubProducer implements KafkaProducerPort {
    private static final Logger log = LoggerFactory.getLogger(KafkaStubProducer.class);

    @Override
    public Mono<Void> send(String topic, String key, String value) {
        log.info("[STUB] Simulating Kafka send: topic={}, key={}, value={}", topic, key, value);
        return Mono.empty();
    }
}
