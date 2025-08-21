// ActiveMqStubConsumer.java
package com.resilient.messaging;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Profile({"local", "dev"})
public class ActiveMqStubConsumer implements ActiveMqConsumerPort {
    private static final Logger log = LoggerFactory.getLogger(ActiveMqStubConsumer.class);

    @Override
    public Flux<ReactiveActiveMqConsumer.MessageRecord> receiveMessages(String destination) {
        return Flux.interval(Duration.ofSeconds(5))
                .map(i -> new ReactiveActiveMqConsumer.MessageRecord(
                        destination, "Test message " + i + " for " + destination))
                .doOnNext(record ->
                        log.info("[STUB] ActiveMQ received from {}: {}", record.destination(), record.message()));
    }
}
