package com.resilient.messaging;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive wrapper for JMS (ActiveMQ) send operations.
 * Adds correlation & arbitrary headers; sets JMSCorrelationID. Prod-only (excluded in local/dev).
 */
@Service
@Profile("!local & !dev")
public class ReactiveActiveMqProducer implements ActiveMqProducerPort {
    private static final Logger log = LoggerFactory.getLogger(ReactiveActiveMqProducer.class);

    @Autowired
    private JmsTemplate jmsTemplate;

    @Override
    public Mono<Void> sendMessage(String destination, String message) {
        return sendMessage(destination, message, Map.of());
    }

    @Override
    public Mono<Void> sendMessage(String destination, String message, Map<String, String> headers) {
        Map<String, String> traced = com.resilient.messaging.TracingHeaderUtil.ensureTracing(headers);
        return Mono.fromRunnable(() -> jmsTemplate.send(destination, session -> {
                    var textMessage = session.createTextMessage(message);
                    // Correlation ID
                    String correlationId = traced != null && traced.containsKey("correlationId")
                            ? traced.get("correlationId")
                            : UUID.randomUUID().toString();
                    textMessage.setJMSCorrelationID(correlationId);
                    if (traced != null) {
                        traced.forEach((k, v) -> {
                            if (v != null && !k.equalsIgnoreCase("correlationId")) {
                                try {
                                    textMessage.setStringProperty(k, v);
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    }
                    log.debug(
                            "ActiveMQ send destination={} correlationId={} size={}bytes",
                            destination,
                            correlationId,
                            message.length());
                    return textMessage;
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
