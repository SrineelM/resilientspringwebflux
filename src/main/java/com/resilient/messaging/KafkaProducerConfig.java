package com.resilient.messaging;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

/**
 * KafkaProducerConfig sets up Kafka producer configuration for local development using Reactor Kafka.
 *
 * <p>This configuration class provides beans for sending messages to Kafka topics reactively. It is
 * designed to work with a local Kafka broker running on localhost:9092. The beans defined here are
 * used by producer services to send messages in a non-blocking, event-driven manner.
 *
 * <h2>Backpressure: maxInFlight</h2>
 * <p>The {@link SenderOptions} is configured with {@code maxInFlight} to cap the number of
 * Kafka send requests that are unacknowledged by the broker at any point in time.
 * Without this setting, a burst of producer calls can queue an unlimited number of records
 * in memory inside the sender, eventually causing OOM under sustained high throughput.
 *
 * <p>With {@code maxInFlight(256)}, once 256 records are awaiting broker acknowledgment,
 * subsequent {@code kafkaSender.send(...)} calls will signal backpressure to their callers
 * (e.g., the outbox dispatcher's {@code flatMap} concurrency cap), throttling production
 * naturally without requiring explicit buffering or dropping.
 *
 * <p>Note: Only producer (sender) beans are defined here. Consumer beans are in
 * {@link KafkaConsumerConfig}.
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * Maximum number of unacknowledged sends allowed in-flight at once.
     * Controls memory usage and provides a natural backpressure signal to callers.
     * Default: 256 records.
     */
    @Value("${messaging.kafka.producer.max-in-flight:256}")
    private int maxInFlight;

    /**
     * Configures {@link SenderOptions} for {@link KafkaSender}.
     *
     * <p>SenderOptions holds the configuration properties for the Kafka producer, such as the
     * broker address and serializers. The {@code maxInFlight} setting is applied here to provide
     * bounded in-flight send requests and natural backpressure under burst load.
     *
     * @return SenderOptions for String key/value messages, with maxInFlight configured.
     */
    @Bean
    public SenderOptions<String, String> senderOptions() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        // [BACKPRESSURE] maxInFlight caps unacknowledged send requests to prevent OOM under burst.
        // See class-level Javadoc for details.
        return SenderOptions.<String, String>create(props).maxInFlight(maxInFlight);
    }

    /**
     * Creates a {@link KafkaSender} bean for sending messages to Kafka topics.
     *
     * <p>KafkaSender is the main entry point for producing messages reactively. It uses the
     * configured SenderOptions (including maxInFlight) to connect to Kafka and send messages
     * in a non-blocking way.
     *
     * <p><strong>Important</strong>: only one {@link KafkaSender} bean should exist per
     * application. Creating additional instances (e.g., inside service methods) opens new
     * Kafka producer connections and is wasteful. All sending should go through this shared bean.
     *
     * @param senderOptions The configuration for the Kafka producer (with maxInFlight set).
     * @return A shared KafkaSender bean.
     */
    @Bean
    public KafkaSender<String, String> kafkaSender(SenderOptions<String, String> senderOptions) {
        return KafkaSender.create(senderOptions);
    }
}
