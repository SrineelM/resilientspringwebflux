// ActiveMqStubProducer.java
package com.resilient.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@Profile({"local", "dev"})
public class ActiveMqStubProducer implements ActiveMqProducerPort {
    private static final Logger log = LoggerFactory.getLogger(ActiveMqStubProducer.class);

    @Override
    public Mono<Void> sendMessage(String destination, String message) { return sendMessage(destination, message, Map.of()); }

    public Mono<Void> sendMessage(String destination, String message, Map<String,String> headers) {
        String safeMsg = message.replaceAll("[\n\r]", "");
        log.info("[STUB] ActiveMQ send dest={} correlationId={} headers={} message={}", destination, headers.getOrDefault("correlationId","-"), headers, safeMsg);
        return Mono.empty();
    }
}
