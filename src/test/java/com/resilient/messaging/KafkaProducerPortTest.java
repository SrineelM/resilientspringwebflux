package com.resilient.messaging;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class KafkaProducerPortTest {
    static class TestKafkaProducerPort implements KafkaProducerPort {
        @Override
        public Mono<Void> send(String topic, String key, String value) {
            return Mono.empty();
        }
    }

    @Test
    void send_happyPath() {
        KafkaProducerPort port = new TestKafkaProducerPort();
        StepVerifier.create(port.send("topic", "key", "value")).verifyComplete();
    }
}
