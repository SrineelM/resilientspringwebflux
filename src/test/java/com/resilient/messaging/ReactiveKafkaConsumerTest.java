package com.resilient.messaging;

import static org.mockito.Mockito.*;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.test.StepVerifier;

/**
 * Unit tests for the refactored {@link ReactiveKafkaConsumer}.
 *
 * <p>The key change being tested is that the consumer now uses {@link KafkaReceiver} instead of
 * {@code @KafkaListener}, giving true end-to-end backpressure and safe offset acknowledgment.
 */
@ExtendWith(MockitoExtension.class)
class ReactiveKafkaConsumerTest {

    @Mock
    KafkaReceiver<String, String> kafkaReceiver;

    @Mock
    KafkaSender<String, String> kafkaSender;

    // concurrencyLimit=4 supplied directly; @Value is not injected in plain Mockito unit tests.
    private ReactiveKafkaConsumer newConsumer() {
        return new ReactiveKafkaConsumer(kafkaReceiver, kafkaSender, "-dlq", 4);
    }

    /**
     * Verifies that receiveMessages() subscribes to KafkaReceiver and the pipeline completes when
     * the receiver emits no records.
     */
    @Test
    void receiveMessages_emptyReceiver_completesNormally() {
        when(kafkaReceiver.receive()).thenReturn(Flux.empty());

        StepVerifier.create(newConsumer().receiveMessages()).verifyComplete();

        verify(kafkaReceiver, times(1)).receive();
    }

    /**
     * Verifies that a record is processed and its offset acknowledged on success.
     *
     * <p>This is the core correctness fix: offset must be acknowledged AFTER processing,
     * not on listener-method return (as was the case with the old @KafkaListener approach).
     */
    @Test
    void receiveMessages_singleRecord_acknowledgesOffset() {
        // Create a mock ReceiverRecord with a trackable ReceiverOffset
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("demo-topic", 0, 0L, "key1", "value1");

        ReceiverOffset mockOffset = mock(ReceiverOffset.class);
        ReceiverRecord<String, String> receiverRecord = new ReceiverRecord<>(consumerRecord, mockOffset);

        when(kafkaReceiver.receive()).thenReturn(Flux.just(receiverRecord));

        StepVerifier.create(newConsumer().receiveMessages()).expectNextCount(1).verifyComplete();

        // Offset must have been acknowledged after processing — this was the core bug:
        // @KafkaListener committed offset on method return, before reactive pipeline completed.
        verify(mockOffset, times(1)).acknowledge();
    }

    /**
     * Verifies that all records from KafkaReceiver are emitted through receiveMessages()
     * and each gets its offset acknowledged.
     */
    @Test
    void receiveMessages_multipleRecords_emitsAll() {
        ConsumerRecord<String, String> cr1 = new ConsumerRecord<>("demo-topic", 0, 0L, "k1", "v1");
        ConsumerRecord<String, String> cr2 = new ConsumerRecord<>("demo-topic", 0, 1L, "k2", "v2");

        ReceiverOffset offset1 = mock(ReceiverOffset.class);
        ReceiverOffset offset2 = mock(ReceiverOffset.class);

        ReceiverRecord<String, String> rr1 = new ReceiverRecord<>(cr1, offset1);
        ReceiverRecord<String, String> rr2 = new ReceiverRecord<>(cr2, offset2);

        when(kafkaReceiver.receive()).thenReturn(Flux.just(rr1, rr2));

        StepVerifier.create(newConsumer().receiveMessages()).expectNextCount(2).verifyComplete();

        // Both offsets acknowledged
        verify(offset1, times(1)).acknowledge();
        verify(offset2, times(1)).acknowledge();
    }
}
