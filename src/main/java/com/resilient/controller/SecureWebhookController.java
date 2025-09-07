package com.resilient.controller;

import com.resilient.security.ReactiveRateLimiter;
import com.resilient.security.WebhookSignatureValidator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * A secure, resilient controller for handling incoming webhook events from external services.
 *
 * <p>This controller implements several security best practices:
 * <ul>
 *   <li><b>Rate Limiting:</b> Protects against denial-of-service attacks by limiting requests per IP.</li>
 *   <li><b>Timestamp Validation:</b> Prevents replay attacks by rejecting old requests.</li>
 *   <li><b>Signature Validation:</b> Ensures the webhook payload is authentic and has not been tampered with.</li>
 *   <li><b>Circuit Breaker:</b> Isolates the application from failures in downstream processing.</li>
 * </ul>
 * It processes requests asynchronously using Project Reactor to maintain high throughput.
 */
@RestController
@RequestMapping("/api/webhook")
@Observed
public class SecureWebhookController {

    // Logger for this controller.
    private static final Logger log = LoggerFactory.getLogger(SecureWebhookController.class);

    private final ReactiveRateLimiter reactiveRateLimiter;
    private final WebhookSignatureValidator signatureValidator;

    /**
     * Constructs the controller with necessary security components.
     *
     * @param reactiveRateLimiter Service for rate-limiting incoming requests.
     * @param signatureValidator Service for validating webhook signatures.
     */
    public SecureWebhookController(
            ReactiveRateLimiter reactiveRateLimiter, WebhookSignatureValidator signatureValidator) {
        this.reactiveRateLimiter = reactiveRateLimiter;
        this.signatureValidator = signatureValidator;
    }

    /**
     * Processes an incoming webhook event.
     *
     * <p>This endpoint performs a series of validation checks before processing the payload.
     * The entire processing pipeline is wrapped in a Resilience4j Circuit Breaker to protect
     * the system from repeated failures.
     *
     * @param headers The map of all HTTP request headers.
     * @param payload A {@link Mono} containing the raw request body as a string.
     * @return A {@link Mono} of {@link ResponseEntity}. It returns 202 Accepted on success,
     *         or an appropriate error status (400, 401, 429, 500) on failure.
     */
    @PostMapping(value = "/event", consumes = MediaType.APPLICATION_JSON_VALUE)
    @CircuitBreaker(name = "webhook-processor") // Wraps the method in a circuit breaker.
    public Mono<ResponseEntity<String>> processEvent(
            @RequestHeader Map<String, String> headers, @RequestBody Mono<String> payload) {

        // Attempt to get the real client IP, falling back to "unknown".
        String ip = headers.getOrDefault("x-forwarded-for", headers.getOrDefault("x-real-ip", "unknown"));
        // Get the timestamp header sent by the webhook provider.
        String timestampStr = headers.getOrDefault("x-webhook-timestamp", "0");

        log.info("Processing webhook event from IP: {}", ip);

        // --- 1. Timestamp Freshness Check (Anti-Replay) ---
        try {
            // Parse the timestamp from the header.
            long timestamp = Long.parseLong(timestampStr);
            // Check if the timestamp is within a 5-second window of the current server time.
            if (Math.abs(System.currentTimeMillis() - timestamp) > 5000) {
                log.warn("Expired request from IP: {}, timestamp: {}", ip, timestamp);
                // Reject requests that are too old or too far in the future.
                return Mono.just(ResponseEntity.badRequest().body("Expired request"));
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp from IP: {}, timestamp: {}", ip, timestampStr);
            // Reject requests with a malformed timestamp header.
            return Mono.just(ResponseEntity.badRequest().body("Invalid timestamp"));
        }

        // --- 2. Rate Limiting Check ---
        // Start the main reactive pipeline.
        return reactiveRateLimiter
                .isAllowed(ip) // Check if the IP is allowed to make a request.
                .flatMap(allowed -> {
                    if (!allowed) {
                        // If rate limit is exceeded, log it and return 429 Too Many Requests.
                        log.warn("Rate limit exceeded for IP: {}", ip);
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body("Rate limit exceeded"));
                    }
                    // --- 3. Signature Validation ---
                    // First, validate the static shared secret (if configured).
                    return signatureValidator
                            .validateStaticSecret(headers)
                            // Then, validate the HMAC signature of the payload.
                            // The payload processing is moved to a bounded elastic scheduler to avoid blocking the event loop.
                            .then(payload.publishOn(Schedulers.boundedElastic()).flatMap(body -> signatureValidator
                                    .validateHmacSignature(body, headers.getOrDefault("x-webhook-signature", ""))
                                    // If all validations pass, proceed to the actual business logic.
                                    .then(processWebhookPayload(body))
                                    // On success, return a 202 Accepted response.
                                    .thenReturn(ResponseEntity.accepted().body("Event processed"))));
                })
                .timeout(Duration.ofSeconds(30)) // Set a 30-second timeout for the entire operation.
                // --- 4. Error Handling ---
                // Catch specific security exceptions (e.g., bad signature) and return 401 Unauthorized.
                .onErrorResume(SecurityException.class, ex -> {
                    log.error("Security validation failed for IP: {}, error: {}", ip, ex.getMessage());
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage()));
                })
                // Catch any other unexpected exceptions and return 500 Internal Server Error.
                .onErrorResume(Exception.class, ex -> {
                    log.error("Unexpected error processing webhook from IP: {}", ip, ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Internal server error"));
                });
    }

    /**
     * Placeholder for the actual business logic that processes the webhook payload.
     * This method is executed on a separate thread pool to avoid blocking the network I/O thread.
     *
     * @param payload The validated webhook payload.
     * @return A {@link Mono} that completes when processing is finished.
     */
    private Mono<Void> processWebhookPayload(String payload) {
        // Wrap potentially blocking business logic in a Mono.
        return Mono.fromRunnable(() -> {
            // Log a truncated version of the payload for diagnostics.
            log.info("Processing webhook payload: {}", payload.substring(0, Math.min(100, payload.length())));

            // Example processing - replace with your business logic
            // - Parse JSON payload
            // - Validate event type
            // - Store event in database
            // - Send notifications
            // - Update external systems
        }).then(); // `then()` returns a Mono<Void> that completes when the Runnable is done.
    }
}
