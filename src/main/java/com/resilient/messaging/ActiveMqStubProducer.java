// ActiveMqStubProducer.java
package com.resilient.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Profile({"local", "dev"})
public class ActiveMqStubProducer implements ActiveMqProducerPort {
    private static final Logger log = LoggerFactory.getLogger(ActiveMqStubProducer.class);

    @Override
    public Mono<Void> sendMessage(String destination, String message) {
        String safeMsg = message.replaceAll("[\n\r]", "");
        log.info("[STUB] Simulating ActiveMQ send: destination={}, message={}", destination, safeMsg);
        return Mono.empty();
    }
}
