package com.resilient.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.kafka.sender.KafkaSender;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactiveKafkaProducerTest {
    @Mock
    KafkaSender<String,String> sender;

    @InjectMocks
    ReactiveKafkaProducer producer = new ReactiveKafkaProducer(sender, "-dlq");

    @Test
    void sendWithHeadersHappyPath() {
        when(sender.send(any())).thenReturn(reactor.core.publisher.Flux.empty());
        StepVerifier.create(producer.sendWithHeaders("topic","value", java.util.Map.of("k","v")))
            .verifyComplete();
        verify(sender).send(any());
    }
}
