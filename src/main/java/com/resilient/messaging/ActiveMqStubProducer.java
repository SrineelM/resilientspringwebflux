// ActiveMqStubProducer.java
package com.resilient.messaging;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * A stub implementation of an ActiveMQ producer for local development and testing.
 *
 * <p>This component is only active in the "local" and "dev" profiles. Its purpose is to simulate
 * sending messages to an ActiveMQ destination without requiring a real message broker to be
 * running. This simplifies the local development setup and allows developers to work on
 * message-producing logic in isolation.
 *
 * <p>It implements the {@link ActiveMqProducerPort}, making it a swappable replacement for the
 * real {@link ReactiveActiveMqProducer}. Instead of sending a message, it simply logs the
 * details to the console.
 */
@Component
@Profile({"local", "dev"})
public class ActiveMqStubProducer implements ActiveMqProducerPort {
    private static final Logger log = LoggerFactory.getLogger(ActiveMqStubProducer.class);

    /**
     * Simulates sending a message to the specified destination by logging it.
     * This is an overload that defaults to having no headers.
     *
     * @param destination The target destination (queue or topic).
     * @param message The message content.
     * @return A {@link Mono} that completes immediately.
     */
    @Override
    public Mono<Void> sendMessage(String destination, String message) {
        return sendMessage(destination, message, Map.of());
    }

    /**
     * Simulates sending a message with headers to the specified destination by logging it.
     *
     * <p>This method does not perform any network operations. It logs the destination,
     * correlation ID (if present), headers, and a sanitized version of the message.
     *
     * @param destination The target destination (queue or topic).
     * @param message The message content.
     * @param headers A map of headers to include with the message.
     * @return A {@link Mono} that completes immediately.
     */
    @Override
    public Mono<Void> sendMessage(String destination, String message, Map<String, String> headers) {
        // Sanitize the message to remove newlines for cleaner logging.
        String safeMsg = message.replaceAll("[\n\r]", "");
        log.info(
                "[STUB] ActiveMQ send dest={} correlationId={} headers={} message={}",
                destination,
                headers.getOrDefault("correlationId", "-"),
                headers,
                safeMsg);
        // Return a Mono that completes immediately, simulating a fire-and-forget send.
        return Mono.empty();
    }
}
