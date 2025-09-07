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

@Component
@Profile("!local & !dev")
public class ReactiveActiveMqConsumer implements ActiveMqConsumerPort {
    private static final Logger log = LoggerFactory.getLogger(ReactiveActiveMqConsumer.class);
    // Record now includes correlation id, headers, and traceparent
    public record MessageRecord(
            String destination, String message, String correlationId, Map<String, String> headers, String traceparent) {
        /**
         * Secondary constructor for backward compatibility with stubs that may not provide a traceparent.
         */
        public MessageRecord(String destination, String message, String correlationId, Map<String, String> headers) {
            this(destination, message, correlationId, headers, null);
        }
    }

    private final Sinks.Many<MessageRecord> messageSink =
            Sinks.many().multicast().onBackpressureBuffer();
    private final JmsTemplate jmsTemplate;

    @Value("${activemq.dlq.destination:ActiveMQ.DLQ}")
    private String dlqDestination;

    public ReactiveActiveMqConsumer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public Flux<MessageRecord> receiveMessages(String destination) {
        return messageSink.asFlux().filter(record -> record.destination().equals(destination));
    }

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
            messageSink.tryEmitNext(new MessageRecord(destination, body, correlationId, headers, traceParent));
        } catch (Exception e) {
            // Send to DLQ with original correlation id + error info
            try {
                final String cid;
                try {
                    cid = raw.getJMSCorrelationID();
                } catch (Exception ignore) {
                    throw new IllegalStateException("No correlation id", ignore);
                }
                final String errMsg = e.getMessage();
                jmsTemplate.send(dlqDestination, session -> {
                    var msg = session.createTextMessage("DLQ:" + errMsg);
                    if (cid != null) msg.setJMSCorrelationID(cid);
                    msg.setStringProperty(
                            "originalDestination",
                            raw.getJMSDestination() != null
                                    ? raw.getJMSDestination().toString()
                                    : "unknown");
                    msg.setStringProperty("error", errMsg);
                    return msg;
                });
                log.warn("ActiveMQ message routed to DLQ correlationId={} reason={} ", cid, e.toString());
            } catch (Exception inner) {
                log.error("Failed to route message to DLQ: {}", inner.toString());
            }
        }
    }
}
