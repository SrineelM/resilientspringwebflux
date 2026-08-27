package com.resilient.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/**
 * ReactiveKafkaConsumer consumes Kafka messages reactively using Reactor Kafka's {@link KafkaReceiver}.
 *
 * <p>This service replaces the previous {@code @KafkaListener}-based approach, which had two
 * critical backpressure and correctness problems:
 * <ol>
 *   <li><strong>Premature offset commit</strong>: Spring committed Kafka offsets as soon as the
 *       {@code void listen()} method returned — <em>before</em> the reactive pipeline finished
 *       processing. A crash between commit and processing completion caused silent message loss.</li>
 *   <li><strong>Unbounded fire-and-forget subscriptions</strong>: Each incoming message started a
 *       new {@code Mono.subscribe()} with no backpressure signal back to the Kafka poll loop,
 *       allowing unlimited in-memory queuing under sustained load.</li>
 * </ol>
 *
 * <h2>Backpressure Strategy</h2>
 * <p>{@link KafkaReceiver#receive()} returns a {@link Flux} that implements the Reactive Streams
 * publisher contract. The Kafka poll loop respects downstream {@code request(n)} signals — it
 * fetches new records only when the pipeline is ready to process them. This provides true
 * end-to-end backpressure from the consumer application all the way back to the Kafka broker's
 * fetch throttle.
 *
 * <p>Concurrency is capped via {@code flatMap(..., concurrencyLimit)}, which allows at most
 * {@code concurrencyLimit} records to be processed in parallel without unbounded queuing.
 *
 * <p>Offset acknowledgment is chained to successful processing via
 * {@code record.receiverOffset().acknowledge()} — ensuring that offsets are only committed
 * after the business logic completes, preventing silent message loss.
 *
 * <p>Key points:
 * <ul>
 *   <li>Uses {@link KafkaReceiver} (Reactor Kafka) for true reactive, backpressure-aware consumption.</li>
 *   <li>Processes each message on a bounded-elastic scheduler to avoid blocking the Kafka poll thread.</li>
 *   <li>Acknowledges offsets only after successful processing.</li>
 *   <li>Routes failed messages to a Dead-Letter Queue (DLQ) before acknowledging.</li>
 *   <li>Add business logic in {@link #processRecord(ReceiverRecord)} for custom processing.</li>
 * </ul>
 */
@Service
@Profile("!local & !dev") // Active only outside local/dev; stub (KafkaStubConsumer) runs in local/dev.
public class ReactiveKafkaConsumer implements KafkaConsumerPort {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveKafkaConsumer.class);

    private final KafkaReceiver<String, String> kafkaReceiver;
    private final KafkaSender<String, String> kafkaSender;
    private final String dlqSuffix;

    /**
     * Maximum number of records processed concurrently within the reactive pipeline.
     *
     * <p><strong>Backpressure note</strong>: this cap directly controls how many in-flight
     * {@code processRecord} calls can exist at once. Combined with {@link KafkaReceiver}'s
     * pull-based fetch, it prevents unbounded queuing and gives the Kafka broker a natural
     * throttle signal.
     */
    private final int concurrencyLimit;

    public ReactiveKafkaConsumer(
            KafkaReceiver<String, String> kafkaReceiver,
            KafkaSender<String, String> kafkaSender,
            @Value("${messaging.kafka.dlq-suffix:-dlq}") String dlqSuffix,
            @Value("${messaging.kafka.consumer.concurrency:4}") int concurrencyLimit) {
        this.kafkaReceiver = kafkaReceiver;
        this.kafkaSender = kafkaSender;
        this.dlqSuffix = dlqSuffix;
        this.concurrencyLimit = concurrencyLimit;
    }

    /**
     * Returns a reactive {@link Flux} of all consumed Kafka messages.
     *
     * <p>This method wires the full consumer pipeline. Call {@code .subscribe()} on the
     * returned Flux (once, at application startup or via a lifecycle bean) to start consuming.
     *
     * <p><strong>Backpressure</strong>: The returned Flux delegates to
     * {@link KafkaReceiver#receive()}, which uses Reactive Streams demand signals to pace
     * the Kafka poll loop. {@code flatMap(..., concurrencyLimit)} further caps parallelism.
     *
     * @return a Flux of {@link ReceiverRecord}s with processing and DLQ routing wired in.
     */
    @Override
    public Flux<ReceiverRecord<String, String>> receiveMessages() {
        // [BACKPRESSURE] KafkaReceiver.receive() is a Reactive Streams publisher.
        // It fetches records from Kafka only when downstream requests(n) — i.e. when the
        // pipeline has capacity to process them. This is true end-to-end backpressure.
        return kafkaReceiver
                .receive()
                // [BACKPRESSURE] flatMap with concurrencyLimit caps parallel processing.
                // At most `concurrencyLimit` records are processed simultaneously; additional
                // incoming records are held by the Kafka fetch buffer (not queued in memory here).
                .flatMap(
                        record -> processRecord(record)
                                // [CORRECTNESS - FIX] Offset is acknowledged ONLY after processing succeeds.
                                // Previously (with @KafkaListener), offsets were committed immediately on
                                // listen() return, before the reactive pipeline finished — causing silent
                                // message loss on crash.
                                .doOnSuccess(v -> record.receiverOffset().acknowledge())
                                // On failure: route to DLQ first, then acknowledge to advance the offset
                                // (so a poisoned message does not block the partition indefinitely).
                                .onErrorResume(ex -> sendToDlq(record, ex).doOnSuccess(v -> record.receiverOffset()
                                        .acknowledge()))
                                // thenReturn(record) preserves the Flux<ReceiverRecord> contract:
                                // processRecord returns Mono<Void>; we re-emit the record so that
                                // the flatMap's output type matches Flux<ReceiverRecord<String,String>>.
                                .thenReturn(record),
                        concurrencyLimit)
                // Run processing off the Kafka poll thread to keep it free for fetching.
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Processes a single consumed Kafka record.
     *
     * <p>Add business logic here (e.g., save to DB, call downstream services).
     * This method runs on a bounded-elastic thread, so it may perform blocking I/O if necessary.
     *
     * @param record the consumed Kafka receiver record (includes offset metadata)
     * @return a {@link Mono<Void>} that completes when processing is done
     */
    private Mono<Void> processRecord(ReceiverRecord<String, String> record) {
        return Mono.fromRunnable(() -> logger.info(
                        "Received message: key={}, value={}, partition={}, offset={}",
                        record.key(),
                        record.value(),
                        record.partition(),
                        record.offset()))
                // Add business logic here — e.g. flatMap to a service call.
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * Sends a failed record to the Dead-Letter Queue (DLQ).
     *
     * <p>Enriches the DLQ record with exception class, message, original partition, and offset
     * headers for downstream debugging.
     *
     * @param original the original receiver record that failed processing
     * @param ex       the exception that caused the failure
     * @return a {@link Mono<Void>} that completes when the DLQ send finishes
     */
    private Mono<Void> sendToDlq(ReceiverRecord<String, String> original, Throwable ex) {
        try {
            String dlqTopic = original.topic() + dlqSuffix;
            ProducerRecord<String, String> pr = new ProducerRecord<>(dlqTopic, original.key(), original.value());
            pr.headers().add("x-exception", ex.getClass().getName().getBytes());
            pr.headers()
                    .add(
                            "x-exception-message",
                            ex.getMessage() == null
                                    ? new byte[0]
                                    : ex.getMessage().getBytes());
            pr.headers()
                    .add(
                            "x-original-partition",
                            String.valueOf(original.partition()).getBytes());
            pr.headers()
                    .add("x-original-offset", String.valueOf(original.offset()).getBytes());
            return kafkaSender
                    .send(Mono.just(SenderRecord.create(pr, original.key())))
                    .then();
        } catch (Exception secondary) {
            logger.error("Failed to route message to DLQ after consumption failure: {}", secondary.getMessage());
            return Mono.empty();
        }
    }
}
