// KafkaStubConsumer.java
package com.resilient.messaging;

import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;

@Component
@Profile({"local", "dev"})
public class KafkaStubConsumer implements KafkaConsumerPort {
    private static final Logger log = LoggerFactory.getLogger(KafkaStubConsumer.class);

    @Override
    public Flux<ReceiverRecord<String, String>> receiveMessages() {
        return Flux.interval(Duration.ofSeconds(3))
                .map(i -> createMockRecord(i))
                .doOnNext(record -> {
                    log.info(
                            "[STUB] Kafka received: topic={}, key={}, value={}",
                            record.topic(),
                            record.key(),
                            record.value());
                    record.receiverOffset().acknowledge();
                });
    }

    private ReceiverRecord<String, String> createMockRecord(long index) {
        String topic = "stub-topic";
        int partition = 0;
        long offset = index;
        String key = "key-" + index;
        String value = "value-" + index;
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(topic, partition, offset, key, value);
        // Use a stub ReceiverOffset implementation for local/dev
        ReceiverOffset stubOffset = new ReceiverOffset() {
            @Override
            public TopicPartition topicPartition() {
                return new TopicPartition(topic, partition);
            }

            @Override
            public long offset() {
                return offset;
            }

            @Override
            public void acknowledge() {
                /* no-op for stub */
            }

            @Override
            public reactor.core.publisher.Mono<Void> commit() {
                return reactor.core.publisher.Mono.empty();
            }
        };
        return new ReceiverRecord<>(consumerRecord, stubOffset);
    }
}
