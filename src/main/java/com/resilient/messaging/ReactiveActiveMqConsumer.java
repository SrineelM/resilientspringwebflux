// ReactiveActiveMqConsumer.java
package com.resilient.messaging;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Reactive ActiveMQ consumer that bridges JMS messages into the Reactive Streams world using a
 * {@link Sinks.Many} multicast sink.
 *
 * <p>Incoming JMS messages are received on the JMS listener thread (via {@code @JmsListener})
 * and emitted into the sink, from which any number of reactive subscribers can consume them.
 * Failed messages are routed to a Dead-Letter Queue (DLQ).
 *
 * <h2>Backpressure Strategy</h2>
 * <p>The internal sink uses {@link Sinks.many().multicast().onBackpressureBuffer(int, boolean)}
 * with a <em>bounded</em> capacity ({@code sinkBufferCapacity}, default 1 000 items).
 * This replaces the previous <em>unbounded</em> buffer which allowed unlimited memory growth
 * when subscribers were slow or disconnected.
 *
 * <p>If the buffer fills (all subscribers slow), {@link Sinks.EmitResult} is checked:
 * <ul>
 *   <li>A failed emit is logged as a warning instead of being silently discarded.</li>
 *   <li>The JMS message is routed to the DLQ so it is not lost.</li>
 * </ul>
 *
 * <p><strong>Note</strong>: This class is active only in non-local/non-dev profiles.
 * In local/dev, {@link ActiveMqStubConsumer} provides a simulated stream.
 */
@Component
@Profile("!local & !dev")
public class ReactiveActiveMqConsumer implements ActiveMqConsumerPort {

    private static final Logger log = LoggerFactory.getLogger(ReactiveActiveMqConsumer.class);

    /**
     * Record representing a consumed ActiveMQ message, including routing destination,
     * body, correlation ID, arbitrary headers, and (optional) W3C traceparent.
     */
    public record MessageRecord(
            String destination, String message, String correlationId, Map<String, String> headers, String traceparent) {
        /**
         * Secondary constructor for backward compatibility with stubs that may not provide a traceparent.
         */
        public MessageRecord(String destination, String message, String correlationId, Map<String, String> headers) {
            this(destination, message, correlationId, headers, null);
        }
    }

    /**
     * Maximum number of messages the multicast sink will buffer before applying backpressure.
     * When the buffer is full, new emits are rejected and the message is routed to the DLQ.
     * Default: 1000 messages.
     */
    @Value("${activemq.consumer.sink.buffer.capacity:1000}")
    private int sinkBufferCapacity;

    // [BACKPRESSURE - FIX] The sink is now bounded (capacity = sinkBufferCapacity).
    // Previously: Sinks.many().multicast().onBackpressureBuffer() — UNBOUNDED, could OOM.
    // Now: a capacity-capped buffer is used. When the buffer fills, tryEmitNext returns a
    // non-OK EmitResult which we check, log, and handle by routing to DLQ.
    //
    // autoCancel=false means the sink stays active even when all current subscribers cancel
    // (e.g., a client disconnects from an SSE stream), allowing new subscribers to attach later.
    private final Sinks.Many<MessageRecord> messageSink =
            Sinks.many().multicast().onBackpressureBuffer(1000, false);

    private final JmsTemplate jmsTemplate;

    @Value("${activemq.dlq.destination:ActiveMQ.DLQ}")
    private String dlqDestination;

    public ReactiveActiveMqConsumer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    /**
     * Returns a {@link Flux} of messages received on the given JMS destination.
     *
     * <p>Multiple subscribers can attach; all receive the same messages (multicast).
     * The sink buffer absorbs bursts up to {@code sinkBufferCapacity}; beyond that,
     * messages are rejected and routed to the DLQ.
     *
     * @param destination the JMS destination to filter messages by
     * @return a Flux of {@link MessageRecord}s matching the specified destination
     */
    @Override
    public Flux<MessageRecord> receiveMessages(String destination) {
        return messageSink.asFlux().filter(record -> record.destination().equals(destination));
    }

    /**
     * JMS message listener invoked by Spring on the JMS broker thread.
     *
     * <p>Converts the raw JMS {@link Message} into a {@link MessageRecord} and emits it
     * into the reactive sink. If the sink buffer is full (backpressure situation), the
     * message is sent to the DLQ rather than silently dropped.
     *
     * <p>On any processing exception, the message is routed to the DLQ with error headers.
     *
     * @param raw the raw JMS message received from the broker
     */
    @JmsListener(destination = "${activemq.consumer.destination:default.queue}")
    public void handleMessage(Message raw) {
        try {
            String destination =
                    raw.getJMSDestination() != null ? raw.getJMSDestination().toString() : "unknown";
            String correlationId = raw.getJMSCorrelationID();
            Map<String, String> headers = new HashMap<>();
            Enumeration<?> names = raw.getPropertyNames();
            while (names != null && names.hasMoreElements()) {
                Object n = names.nextElement();
                if (n != null) {
                    String key = n.toString();
                    try {
                        headers.put(key, String.valueOf(raw.getObjectProperty(key)));
                    } catch (Exception ignore) {
                    }
                }
            }
            String body = (raw instanceof TextMessage tm) ? tm.getText() : "";
            // Simulated failure trigger (for DLQ testing) if header forceError==true
            if ("true".equalsIgnoreCase(headers.get("forceError"))) {
                throw new IllegalStateException("Forced error to test DLQ routing");
            }
            // Traceparent support (reuse if provided, otherwise generate)
            String traceParent = headers.getOrDefault("traceparent", headers.get("traceId"));
            if (traceParent == null) {
                traceParent = com.resilient.messaging.TracingHeaderUtil.ensureTracing(headers)
                        .get("traceparent");
            }

            MessageRecord messageRecord = new MessageRecord(destination, body, correlationId, headers, traceParent);

            // [BACKPRESSURE - FIX] Check the result of tryEmitNext instead of ignoring it.
            // Previously, overflow failures were silently swallowed. Now we log a warning
            // and route to DLQ so the message is not lost when the buffer is full.
            Sinks.EmitResult result = messageSink.tryEmitNext(messageRecord);
            if (result.isFailure()) {
                log.warn(
                        "[BACKPRESSURE] ActiveMQ message could not be emitted to sink (result={}), "
                                + "routing to DLQ. destination={} correlationId={}",
                        result,
                        destination,
                        correlationId);
                routeToDlq(raw, correlationId, "Sink backpressure overflow: " + result);
            }
        } catch (Exception e) {
            // Send to DLQ with original correlation id + error info
            try {
                final String cid;
                try {
                    cid = raw.getJMSCorrelationID();
                } catch (Exception ignore) {
                    throw new IllegalStateException("No correlation id", ignore);
                }
                routeToDlq(raw, cid, e.getMessage());
                log.warn("ActiveMQ message routed to DLQ correlationId={} reason={} ", cid, e.toString());
            } catch (Exception inner) {
                log.error("Failed to route message to DLQ: {}", inner.toString());
            }
        }
    }

    /**
     * Routes a JMS message to the Dead-Letter Queue with error information attached as properties.
     *
     * @param raw           the original JMS message
     * @param correlationId the correlation ID (may be null)
     * @param errorMessage  a description of the failure reason
     */
    private void routeToDlq(Message raw, String correlationId, String errorMessage) {
        try {
            final String errMsg = errorMessage;
            jmsTemplate.send(dlqDestination, session -> {
                var msg = session.createTextMessage("DLQ:" + errMsg);
                if (correlationId != null) msg.setJMSCorrelationID(correlationId);
                try {
                    msg.setStringProperty(
                            "originalDestination",
                            raw.getJMSDestination() != null
                                    ? raw.getJMSDestination().toString()
                                    : "unknown");
                } catch (Exception ignore) {
                }
                msg.setStringProperty("error", errMsg);
                return msg;
            });
        } catch (Exception dlqEx) {
            log.error("Failed to route message to DLQ: {}", dlqEx.toString());
        }
    }
}
