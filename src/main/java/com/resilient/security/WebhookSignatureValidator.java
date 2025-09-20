package com.resilient.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Component for validating webhook signatures to ensure incoming webhooks are authentic and untampered.
 * <p>
 * This validator supports two validation methods:
 * <ul>
 *   <li>Static secret validation: Compares a shared secret sent in headers.</li>
 *   <li>HMAC signature validation: Verifies an HMAC-SHA256 signature of the payload.</li>
 * </ul>
 * Both methods are reactive (return Mono<Void>) for integration with WebFlux.
 * <p>
 * Secrets are injected from application properties (e.g., webhook.secret, webhook.hmac-secret).
 * In production, use strong, unique secrets and store them securely (e.g., via environment variables).
 */
@Component
public class WebhookSignatureValidator {

    // The static secret used for simple header-based validation
    private final String webhookSecret;

    // The secret key used for HMAC-SHA256 signature calculation
    private final String hmacSecret;

    /**
     * Constructor that injects webhook secrets from application properties.
     *
     * @param webhookSecret The static secret for header validation (default: "change-me")
     * @param hmacSecret The secret key for HMAC signature validation (default: "change-me")
     */
    public WebhookSignatureValidator(
            @Value("${webhook.secret:change-me}") String webhookSecret,
            @Value("${webhook.hmac-secret:change-me}") String hmacSecret) {
        this.webhookSecret = webhookSecret;
        this.hmacSecret = hmacSecret;
    }

    /**
     * Validates a static secret provided in the webhook headers.
     * <p>
     * This is a simple authentication method where the client sends a shared secret
     * in the "x-webhook-secret" header. It's less secure than HMAC but easier to implement.
     *
     * @param headers The HTTP headers from the webhook request
     * @return Mono<Void> that completes if validation succeeds, or errors with SecurityException if fails
     */
    public Mono<Void> validateStaticSecret(Map<String, String> headers) {
        String providedSecret = headers.getOrDefault("x-webhook-secret", "");
        if (!webhookSecret.equals(providedSecret)) {
            return Mono.error(new SecurityException("Invalid static secret"));
        }
        return Mono.empty(); // Validation successful
    }

    /**
     * Validates an HMAC-SHA256 signature of the webhook payload.
     * <p>
     * This method computes the expected HMAC signature using the payload and secret,
     * then compares it securely with the provided signature to prevent timing attacks.
     * The signature should be Base64-encoded.
     *
     * @param payload The raw webhook payload (JSON string)
     * @param signature The HMAC signature provided in the webhook (Base64-encoded)
     * @return Mono<Void> that completes if validation succeeds, or errors with SecurityException if fails
     */
    public Mono<Void> validateHmacSignature(String payload, String signature) {
        return Mono.fromCallable(() -> {
                    if (signature.isEmpty()) {
                        throw new SecurityException("Missing HMAC signature");
                    }

                    // Compute the expected signature
                    String expected = hmac(payload, hmacSecret);

                    // Use MessageDigest.isEqual for secure comparison (prevents timing attacks)
                    if (!MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                        throw new SecurityException("Invalid HMAC signature");
                    }
                    return true; // Validation successful
                })
                .then(); // Convert to Mono<Void>
    }

    /**
     * Computes an HMAC-SHA256 signature for the given data using the provided secret.
     * <p>
     * This is a private utility method used by validateHmacSignature.
     * The result is Base64-encoded for easy transmission in HTTP headers.
     *
     * @param data The data to sign (e.g., webhook payload)
     * @param secret The secret key for HMAC calculation
     * @return The Base64-encoded HMAC-SHA256 signature
     * @throws IllegalStateException if HMAC calculation fails (shouldn't happen with valid inputs)
     */
    private String hmac(String data, String secret) {
        try {
            // Initialize HMAC-SHA256 Mac instance
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            // Compute the HMAC and encode as Base64
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC calculation error", e);
        }
    }
}
