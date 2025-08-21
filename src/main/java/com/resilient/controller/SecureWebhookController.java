package com.resilient.controller;

import com.resilient.security.ReactiveRateLimiter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

@RestController
@RequestMapping("/api/webhook")
@Observed
public class SecureWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SecureWebhookController.class);

    private final ReactiveRateLimiter reactiveRateLimiter;
    private final String webhookSecret;
    private final String hmacSecret;

    public SecureWebhookController(
            ReactiveRateLimiter reactiveRateLimiter,
            @Value("${webhook.secret:change-me}") String webhookSecret,
            @Value("${webhook.hmac-secret:change-me}") String hmacSecret) {
        this.reactiveRateLimiter = reactiveRateLimiter;
        this.webhookSecret = webhookSecret;
        this.hmacSecret = hmacSecret;
    }

    @PostMapping(value = "/event", consumes = MediaType.APPLICATION_JSON_VALUE)
    @CircuitBreaker(name = "webhook-processor")
    public Mono<ResponseEntity<String>> processEvent(
            @RequestHeader Map<String, String> headers, @RequestBody Mono<String> payload) {

        String ip = headers.getOrDefault("x-forwarded-for", headers.getOrDefault("x-real-ip", "unknown"));
        String timestampStr = headers.getOrDefault("x-webhook-timestamp", "0");

        log.info("Processing webhook event from IP: {}", ip);

        // Timestamp freshness check
        try {
            long timestamp = Long.parseLong(timestampStr);
            if (Math.abs(System.currentTimeMillis() - timestamp) > 5000) {
                log.warn("Expired request from IP: {}, timestamp: {}", ip, timestamp);
                return Mono.just(ResponseEntity.badRequest().body("Expired request"));
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp from IP: {}, timestamp: {}", ip, timestampStr);
            return Mono.just(ResponseEntity.badRequest().body("Invalid timestamp"));
        }

        // Rate limiting check
        return reactiveRateLimiter
                .isAllowed(ip)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("Rate limit exceeded for IP: {}", ip);
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body("Rate limit exceeded"));
                    }

                    return validateStaticSecret(headers)
                            .then(payload.publishOn(Schedulers.boundedElastic()).flatMap(body -> validateHmacSignature(
                                            body, headers.getOrDefault("x-webhook-signature", ""))
                                    .then(processWebhookPayload(body))
                                    .thenReturn(ResponseEntity.accepted().body("Event processed"))));
                })
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(SecurityException.class, ex -> {
                    log.error("Security validation failed for IP: {}, error: {}", ip, ex.getMessage());
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage()));
                })
                .onErrorResume(Exception.class, ex -> {
                    log.error("Unexpected error processing webhook from IP: {}", ip, ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Internal server error"));
                });
    }

    private Mono<Void> validateStaticSecret(Map<String, String> headers) {
        String providedSecret = headers.getOrDefault("x-webhook-secret", "");
        if (!webhookSecret.equals(providedSecret)) {
            return Mono.error(new SecurityException("Invalid static secret"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateHmacSignature(String payload, String signature) {
        return Mono.fromCallable(() -> {
                    if (signature.isEmpty()) {
                        throw new SecurityException("Missing HMAC signature");
                    }

                    String expected = hmac(payload, hmacSecret);
                    if (!MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                        throw new SecurityException("Invalid HMAC signature");
                    }
                    return true;
                })
                .then();
    }

    private Mono<Void> processWebhookPayload(String payload) {
        return Mono.fromRunnable(() -> {
            // Add your actual webhook processing logic here
            log.info("Processing webhook payload: {}", payload.substring(0, Math.min(100, payload.length())));

            // Example processing - replace with your business logic
            // - Parse JSON payload
            // - Validate event type
            // - Store event in database
            // - Send notifications
            // - Update external systems
        });
    }

    private String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC calculation error", e);
        }
    }
}
