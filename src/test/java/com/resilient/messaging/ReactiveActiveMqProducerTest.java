package com.resilient.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import reactor.test.StepVerifier;
import java.util.Map;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactiveActiveMqProducerTest {
    @Mock
    JmsTemplate jmsTemplate;

    @InjectMocks
    ReactiveActiveMqProducer producer;

    @Test
    void sendMessageHappyPath() {
        doNothing().when(jmsTemplate).send(anyString(), any());
        StepVerifier.create(producer.sendMessage("queue","hello", Map.of("correlationId","c1")))
                .verifyComplete();
        verify(jmsTemplate).send(eq("queue"), any());
    }
}
