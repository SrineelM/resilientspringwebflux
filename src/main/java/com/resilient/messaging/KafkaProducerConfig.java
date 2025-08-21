package com.resilient.messaging;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

/**
 * KafkaProducerConfig sets up Kafka producer configuration for local development using Reactor
 * Kafka.
 *
 * <p>This configuration class provides beans for sending messages to Kafka topics reactively. It is
 * designed to work with a local Kafka broker running on localhost:9092. The beans defined here are
 * used by producer services to send messages in a non-blocking, event-driven manner.
 *
 * <p>Note: Only producer (sender) beans are defined here. Consumer beans should be in a separate
 * class.
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * Configures SenderOptions for KafkaSender.
     *
     * <p>SenderOptions holds the configuration properties for the Kafka producer, such as the broker
     * address and serializers. These options are used by KafkaSender to send messages.
     *
     * @return SenderOptions for String key/value messages.
     */
    @Bean
    public SenderOptions<String, String> senderOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return SenderOptions.create(props);
    }

    /**
     * Creates a KafkaSender bean for sending messages to Kafka topics.
     *
     * <p>KafkaSender is the main entry point for producing messages reactively. It uses the
     * configured SenderOptions to connect to Kafka and send messages in a non-blocking way.
     *
     * @param senderOptions The configuration for the Kafka producer.
     * @return A KafkaSender bean.
     */
    @Bean
    public KafkaSender<String, String> kafkaSender(SenderOptions<String, String> senderOptions) {
        return KafkaSender.create(senderOptions);
    }
}
