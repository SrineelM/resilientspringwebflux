package com.resilient.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * KafkaIntegrationController - Local profile only. Simulates Kafka produce/consume for developer
 * testing without a broker.
 */
@RestController
@RequestMapping("/kafka")
@Profile("local")
public class KafkaIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(KafkaIntegrationController.class);

    /** DTO for incoming produce request. */
    public record ProduceRequest(@NotBlank String message) {}

    /** Simulates producing a message to Kafka. */
    @PostMapping("/produce")
    public Mono<ResponseEntity<String>> produce(@Valid @RequestBody ProduceRequest request) {
        return Mono.just(request.message())
                .timeout(Duration.ofSeconds(10))
                .doOnNext(msg -> log.info("Simulating Kafka produce: {}", msg))
                .map(msg -> ResponseEntity.accepted().body("Message produced (simulated): " + msg))
                .onErrorResume(ex -> {
                    log.error("Produce simulation failed", ex);
                    return Mono.just(ResponseEntity.status(500).body("Failed to process message"));
                });
    }

    /** Simulates consuming messages from Kafka. */
    @GetMapping(value = "/consume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> consumeMessages() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(i -> "Demo Kafka message #" + i)
                .take(10)
                .onErrorContinue((ex, val) -> log.error("Kafka consume error", ex));
    }
}
