package com.resilient.messaging;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

/**
 * KafkaConsumerConfig sets up Kafka consumer configuration using Reactor Kafka.
 *
 * <p>This configuration class defines the necessary beans for a reactive Kafka consumer, allowing
 * it to subscribe to topics and receive messages.
 *
 * <p>Note: Only consumer (receiver) beans are defined here. Producer beans should be in a separate
 * class.
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * Configures ReceiverOptions for KafkaReceiver.
     *
     * <p>ReceiverOptions holds configuration properties for the Kafka consumer, such as the broker
     * address, consumer group ID, deserializers, and subscription topics.
     *
     * @return ReceiverOptions for String key/value messages.
     */
    @Bean
    public ReceiverOptions<String, String> receiverOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "reactive-consumer-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");

        return ReceiverOptions.<String, String>create(props).subscription(Collections.singleton("test-topic"));
    }

    /**
     * Creates a KafkaReceiver bean for consuming messages reactively.
     *
     * <p>KafkaReceiver is the main entry point for consuming messages in a non-blocking way. It uses
     * the configured ReceiverOptions to connect to Kafka and subscribe to topics.
     *
     * @param receiverOptions The configuration for the Kafka consumer.
     * @return A KafkaReceiver bean.
     */
    @Bean
    public KafkaReceiver<String, String> kafkaReceiver(ReceiverOptions<String, String> receiverOptions) {
        return KafkaReceiver.create(receiverOptions);
    }
}
