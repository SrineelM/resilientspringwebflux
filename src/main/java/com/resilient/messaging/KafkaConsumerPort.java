// KafkaConsumerPort.java
package com.resilient.messaging;

import reactor.core.publisher.Flux;
import reactor.kafka.receiver.ReceiverRecord;

public interface KafkaConsumerPort {
    Flux<ReceiverRecord<String, String>> receiveMessages();
}
