// ActiveMqProducerPort.java
package com.resilient.messaging;

import reactor.core.publisher.Mono;

public interface ActiveMqProducerPort {
    Mono<Void> sendMessage(String destination, String message);
}
