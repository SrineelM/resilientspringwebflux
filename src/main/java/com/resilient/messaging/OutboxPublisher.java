package com.resilient.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal reactive outbox publisher scaffold. Persists an event to message_outbox table; actual
 * dispatch to Kafka/ActiveMQ (or stub) should be performed by a scheduled poller (not yet implemented).
 * Only added as groundwork - low risk and inert until a poller is added.
 */
@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final DatabaseClient db;

    public OutboxPublisher(DatabaseClient db) { this.db = db; }

    public Mono<Long> persistEvent(String aggregateType,
                                   String aggregateId,
                                   String eventType,
                                   String payload,
                                   Map<String,String> headers) {
        String headerJson = serializeHeaders(headers);
        return db.sql("INSERT INTO message_outbox (aggregate_type, aggregate_id, event_type, payload, headers, status) " +
                        "VALUES (:at,:aid,:et,:pl,:hd,'NEW')")
                .bind("at", aggregateType)
                .bind("aid", aggregateId)
                .bind("et", eventType)
                .bind("pl", payload)
                .bind("hd", headerJson)
                .filter((statement, executeFunction) -> statement.returnGeneratedValues("id").execute())
                .map(row -> row.get("id", Long.class))
                .one()
                .doOnSuccess(id -> log.debug("Outbox event persisted id={} type={} agg={}:{}", id, eventType, aggregateType, aggregateId));
    }

    private String serializeHeaders(Map<String,String> headers) {
        if (headers == null || headers.isEmpty()) return "{}";
        try {
            return new ObjectMapper().writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers, storing empty JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
