// ActiveMqStubConsumer.java
package com.resilient.messaging;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.UUID;

@Component
@Profile({"local", "dev"})
public class ActiveMqStubConsumer implements ActiveMqConsumerPort {
    private static final Logger log = LoggerFactory.getLogger(ActiveMqStubConsumer.class);

    @Override
    public Flux<ReactiveActiveMqConsumer.MessageRecord> receiveMessages(String destination) {
        return Flux.interval(Duration.ofSeconds(5))
                .map(i -> new ReactiveActiveMqConsumer.MessageRecord(
                        destination,
                        "Test message " + i + " for " + destination,
                        UUID.randomUUID().toString(),
                        Map.of("stub","true")))
                .doOnNext(record ->
                        log.info("[STUB] ActiveMQ received dest={} correlationId={} msg={}", record.destination(), record.correlationId(), record.message()));
    }
}
