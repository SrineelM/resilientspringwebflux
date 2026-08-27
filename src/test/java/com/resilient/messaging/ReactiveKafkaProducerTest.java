package com.resilient.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.kafka.sender.KafkaSender;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReactiveKafkaProducerTest {

    @Mock
    KafkaSender<String, String> sender;

    // Construct manually in @BeforeEach so the mock is fully initialised before use.
    // Using @InjectMocks with an inline initializer can result in null mocks depending
    // on Mockito's injection order and the presence of @Value-annotated fields.
    ReactiveKafkaProducer producer;

    @BeforeEach
    void setUp() {
        producer = new ReactiveKafkaProducer(sender, "-dlq");
    }

    @Test
    void sendWithHeadersHappyPath() {
        when(sender.send(any())).thenReturn(reactor.core.publisher.Flux.empty());
        StepVerifier.create(producer.sendWithHeaders("topic", "value", java.util.Map.of("k", "v")))
                .verifyComplete();
        verify(sender).send(any());
    }
}
