// ActiveMqProducerPort.java
package com.resilient.messaging;

import java.util.Map;
import reactor.core.publisher.Mono;

public interface ActiveMqProducerPort {
    Mono<Void> sendMessage(String destination, String message);

    /**
     * Send a message with optional headers/correlation. Implementations should ensure a
     * JMSCorrelationID is set (generate one if not provided in headers under key 'correlationId').
     */
    Mono<Void> sendMessage(String destination, String message, Map<String, String> headers);
}
