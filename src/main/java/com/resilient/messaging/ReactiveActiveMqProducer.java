package com.resilient.messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Example reactive ActiveMQ producer for beginners. Note: ActiveMQ is not natively reactive, so we
 * wrap the send in Mono.fromRunnable.
 *
 * <p>Modified to offload blocking JMS operation to boundedElastic scheduler to prevent blocking the
 * event loop.
 */
@Service
public class ReactiveActiveMqProducer {
    @Autowired
    private JmsTemplate jmsTemplate;

    public Mono<Void> sendMessage(String destination, String message) {
        return Mono.fromRunnable(() -> jmsTemplate.convertAndSend(destination, message))
                .subscribeOn(Schedulers.boundedElastic()) // Offload blocking operation
                .then();
    }
}
