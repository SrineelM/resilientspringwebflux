// ActiveMqConsumerPort.java
package com.resilient.messaging;

import reactor.core.publisher.Flux;

public interface ActiveMqConsumerPort {
    Flux<ReactiveActiveMqConsumer.MessageRecord> receiveMessages(String destination);
}
