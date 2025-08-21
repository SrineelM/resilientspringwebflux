// ReactiveActiveMqConsumer.java
package com.resilient.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@Profile("!local & !dev")
public class ReactiveActiveMqConsumer implements ActiveMqConsumerPort {
    // Custom record to hold message and destination
    public record MessageRecord(String destination, String message) {}

    private final Sinks.Many<MessageRecord> messageSink = Sinks.many().unicast().onBackpressureBuffer();

    @Override
    public Flux<MessageRecord> receiveMessages(String destination) {
        // Filter messages for the requested destination
        return messageSink.asFlux().filter(record -> record.destination.equals(destination));
    }

    @JmsListener(destination = "${activemq.consumer.destination:default.queue}")
    public void handleMessage(String message) {
        // Emit message with destination info
        messageSink.tryEmitNext(new MessageRecord("${activemq.consumer.destination:default.queue}", message));
    }
}
