package com.resilient.controller;

import com.resilient.security.ReactiveRateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A resilient, local-only controller for simulating Kafka interactions.
 *
 * <p>This controller is only active when the "local" profile is enabled. It provides
 * convenient endpoints for developers to test application behavior that depends on
 * producing or consuming Kafka messages, ensuring a lightweight local development experience.
 */
@RestController
@RequestMapping("/kafka")
@Profile("local")
public class DemoKafkaController {

    /**
     * Logger for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(DemoKafkaController.class);

    private final Optional<ReactiveRateLimiter> rateLimiter;

    /**
     * Constructs the controller, injecting an optional rate limiter.
     *
     * @param rateLimiter The reactive rate limiter, which may not be present if no
     *                    implementation (e.g., in-memory, Redis) is configured.
     */
    public DemoKafkaController(Optional<ReactiveRateLimiter> rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * DTO for the message payload to ensure robust validation.
     */
    public record MessageRequest(
            @NotBlank(message = "Message content cannot be blank")
                    @Size(max = 1024, message = "Message content must be less than 1024 characters")
                    String content) {}

    /**
     * Simulates producing a single message to a Kafka topic.
     *
     * <p>This endpoint accepts a JSON payload, validates it, and logs it to simulate
     * sending to Kafka. It is protected by a rate limiter and includes a timeout.
     *
     * @param request The message request DTO containing the content to be "produced".
     * @return A {@link Mono} that completes with a {@link ResponseEntity} indicating success (202 Accepted)
     *         rate-limiting (429 Too Many Requests), or other errors.
     */
    @PostMapping("/produce") // Defines an HTTP POST endpoint at /kafka/produce.
    public Mono<ResponseEntity<String>> produce(@Valid @RequestBody MessageRequest request) {
        // Define the core logic for producing the message.
        Mono<ResponseEntity<String>> produceLogic = Mono.just(request.content())
                // Set a 10-second timeout. If the pipeline doesn't complete by then, it will error out.
                .timeout(Duration.ofSeconds(10))
                // As a side-effect, log the message when it passes through this stage.
                .doOnNext(msg -> log.info("Simulating Kafka produce: {}", msg))
                // On success, transform the message into an HTTP 202 Accepted response.
                .map(msg -> ResponseEntity.accepted().body("Message produced (simulated): " + msg))
                // If any error occurs in the pipeline (e.g., timeout), execute this block.
                .onErrorResume(ex -> {
                    log.error("Produce simulation failed for payload: {}", request.content(), ex); // Log the error.
                    // Return an HTTP 500 Internal Server Error response.
                    return Mono.just(ResponseEntity.status(500).body("Failed to process message"));
                });

        // If a rate limiter is present, wrap the logic with a rate-limiting check.
        if (rateLimiter.isPresent()) {
            // Check if a request is allowed for the "kafka-produce" key.
            return rateLimiter.get().isAllowed("kafka-produce").flatMap(allowed -> {
                if (allowed) {
                    return produceLogic; // If allowed, execute the message production logic.
                } else {
                    // If not allowed, return HTTP 429 Too Many Requests.
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
                }
            });
        }

        // If no rate limiter is configured, execute the logic directly.
        return produceLogic;
    }

    /**
     * Simulates a stream of messages being consumed from a Kafka topic.
     *
     * <p>This endpoint produces a stream of Server-Sent Events (SSE), with each event representing
     * a message from a Kafka topic. It sends 10 messages at one-second intervals and is
     * protected by a rate limiter.
     *
     * @return A {@link Flux} of strings, where each string is a simulated Kafka message,
     *         formatted as a text/event-stream.
     */
    // Defines an HTTP GET endpoint that produces a Server-Sent Event stream.
    @GetMapping(value = "/consume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> consumeMessages() {
        // Create a Flux that emits a new number (0, 1, 2, ...) every second.
        Flux<Long> intervalFlux = Flux.interval(Duration.ofSeconds(1));

        // If a rate limiter is present, apply it to control the message flow.
        // The rate limiter checks if requests are allowed, but doesn't directly limit the Flux.
        // For demo purposes, we'll simulate rate limiting by checking periodically.
        Flux<Long> rateLimitedFlux = rateLimiter
                .map(limiter -> intervalFlux.filterWhen(i -> limiter.isAllowed("kafka-demo")))
                .orElse(intervalFlux);

        return rateLimitedFlux
                // Transform each number into a simulated message string.
                .map(i -> "Demo Kafka message #" + i)
                // Limit the stream to the first 10 messages, then it will complete.
                .take(10)
                // If an error were to occur, log it but allow the stream to continue.
                .onErrorContinue((ex, val) -> log.error("Kafka consume error", ex));
    }
}
