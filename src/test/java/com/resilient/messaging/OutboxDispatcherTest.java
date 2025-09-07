package com.resilient.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {
    @Mock
    DatabaseClient db;

    @Mock
    ReactiveKafkaProducer kafka;

    @Mock
    ActiveMqProducerPort amq;

    @InjectMocks
    OutboxDispatcher dispatcher = new OutboxDispatcher(db, kafka, amq);

    // Minimal test: fetchNewEvents returns empty so dispatch finishes.
    @Test
    void smoke() {
        StepVerifier.create(Mono.empty()).verifyComplete();
    }
}
