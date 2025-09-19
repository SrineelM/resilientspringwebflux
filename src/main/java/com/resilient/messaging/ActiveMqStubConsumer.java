// ActiveMqStubConsumer.java
package com.resilient.messaging;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * A stub implementation of an ActiveMQ consumer for local development and testing.
 *
 * <p>This component is only active in the "local" and "dev" profiles. Its purpose is to simulate
 * receiving messages from an ActiveMQ destination without requiring a real message broker to be
 * running. This simplifies the local development setup and allows developers to work on
 * message-consuming logic in isolation.
 *
 * <p>It implements the {@link ActiveMqConsumerPort}, making it a swappable replacement for the
 * real {@link ReactiveActiveMqConsumer}.
 */
@Component
@Profile({"local", "dev"})
public class ActiveMqStubConsumer implements ActiveMqConsumerPort {
    private static final Logger log = LoggerFactory.getLogger(ActiveMqStubConsumer.class);

    /**
     * Simulates receiving a stream of messages from a given destination.
     *
     * <p>Instead of connecting to a broker, this method generates a new mock {@link ReactiveActiveMqConsumer.MessageRecord}
     * every 5 seconds using {@code Flux.interval}. Each message contains a sample payload, a
     * unique correlation ID, and a "stub" header for easy identification.
     *
     * @param destination The name of the destination to simulate receiving messages from.
     * @return A {@link Flux} that continuously emits mock message records.
     */
    @Override
    public Flux<ReactiveActiveMqConsumer.MessageRecord> receiveMessages(String destination) {
        // Generate a new event every 5 seconds to simulate a message arriving from the queue.
        return Flux.interval(Duration.ofSeconds(5))
                // For each event, create a mock message record.
                .map(i -> new ReactiveActiveMqConsumer.MessageRecord(
                        destination,
                        "Test message " + i + " for " + destination,
                        UUID.randomUUID().toString(),
                        Map.of("stub", "true")))
                // Log the simulated message for visibility during development.
                .doOnNext(record -> log.info(
                        "[STUB] ActiveMQ received dest={} correlationId={} msg={}",
                        record.destination(),
                        record.correlationId(),
                        record.message()));
    }
}
