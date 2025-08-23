package com.resilient.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
class OutboxPublisherTest {
    @Autowired
    OutboxPublisher publisher;
    @Autowired
    DatabaseClient db;

    @Test
    void persistEventInsertsRow() {
        StepVerifier.create(
                publisher.persistEvent("User","123","UserCreated","{\"id\":123}", Map.of("correlationId","test-corr"))
                        .flatMap(id -> db.sql("SELECT status FROM message_outbox WHERE id=:id")
                                .bind("id", id)
                                .map((row,meta) -> row.get("status", String.class)).one())
        ).expectNext("NEW").verifyComplete();
    }
}
