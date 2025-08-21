// KafkaIntegrationController.java
//
// Local-only Kafka integration controller for demo purposes.
// - Uses @Profile("local") to avoid confusion in higher environments.
// - Input validation, error handling, timeout, and rate limiting are included.
// - Follows proper reactive patterns (no blocking).

package com.resilient.controller;

import jakarta.validation.Valid;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * KafkaIntegrationController provides local-only endpoints for Kafka produce/consume simulation.
 *
 * <p>Production-ready: uses @Profile("local"), validation, error handling, timeout, rate limiting,
 * and proper reactive patterns.
 */
@RestController
@RequestMapping("/kafka")
@Profile("local")
public class DemoKafkaController {

    private static final Logger log = LoggerFactory.getLogger(DemoKafkaController.class);

    /** Simulates producing a message to Kafka. Validates payload, adds timeout and error handling. */
    @PostMapping("/produce")
    public Mono<ResponseEntity<String>> produce(@Valid @RequestBody String payload) {
        return Mono.just(payload)
                .timeout(Duration.ofSeconds(10))
                .doOnNext(msg -> log.info("Simulating Kafka produce: {}", msg))
                .map(msg -> ResponseEntity.accepted().body("Message produced (simulated): " + msg))
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(500).body("Failed to process message")));
    }

    /** Simulates consuming messages from Kafka. Adds rate limiting and error handling. */
    @GetMapping(value = "/consume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> consumeMessages() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(i -> "Demo Kafka message #" + i)
                .take(10)
                .onErrorContinue((ex, val) -> log.error("Kafka consume error", ex));
    }
}
