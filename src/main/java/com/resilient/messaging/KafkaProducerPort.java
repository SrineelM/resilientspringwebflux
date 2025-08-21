package com.resilient.messaging;

import reactor.core.publisher.Mono;

public interface KafkaProducerPort {
    Mono<Void> send(String topic, String key, String value);
}
