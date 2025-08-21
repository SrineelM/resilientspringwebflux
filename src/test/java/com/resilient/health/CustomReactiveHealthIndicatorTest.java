package com.resilient.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ActiveProfiles({"dev", "local"}) // Ensure dev/local profiles are active during tests
class CustomReactiveHealthIndicatorTest {
    @Mock
    ConnectionFactory connectionFactory;

    @Mock
    Connection connection;

    @Mock
    ConnectionMetadata metadata;

    CustomReactiveHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        healthIndicator = new CustomReactiveHealthIndicator(connectionFactory);
    }

    @Test
    void health_happyPath() {
        // given: A successful connection that returns metadata and closes properly.
        doReturn(Mono.just(connection)).when(connectionFactory).create();
        when(connection.getMetadata()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        when(metadata.getDatabaseVersion()).thenReturn("1.4.200");
        when(connection.close()).thenReturn(Mono.empty());

        // when & then: The health indicator should report UP with database details.
        StepVerifier.create(healthIndicator.health())
                .assertNext(h -> {
                    assertEquals(Status.UP, h.getStatus());
                    assertEquals("reachable", h.getDetails().get("database"));
                    assertEquals("H2", h.getDetails().get("vendor"));
                    assertEquals("1.4.200", h.getDetails().get("version"));
                })
                .verifyComplete();
    }

    @Test
    void health_whenConnectionFails_returnsDown() {
        // given: The connection factory returns an error, simulating a down database.
        doReturn(Mono.error(new RuntimeException("Connection refused")))
                .when(connectionFactory)
                .create();

        // when & then: The health indicator should report DOWN with an error message.
        StepVerifier.create(healthIndicator.health())
                .assertNext(h -> {
                    assertEquals(Status.DOWN, h.getStatus());
                    assertEquals(
                            "java.lang.RuntimeException: Connection refused",
                            h.getDetails().get("error"));
                })
                .verifyComplete();
    }
}
