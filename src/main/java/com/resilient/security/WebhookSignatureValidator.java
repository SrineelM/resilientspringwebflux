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

@Component
public class WebhookSignatureValidator {

    private final String webhookSecret;
    private final String hmacSecret;

    public WebhookSignatureValidator(
            @Value("${webhook.secret:change-me}") String webhookSecret,
            @Value("${webhook.hmac-secret:change-me}") String hmacSecret) {
        this.webhookSecret = webhookSecret;
        this.hmacSecret = hmacSecret;
    }

    public Mono<Void> validateStaticSecret(Map<String, String> headers) {
        String providedSecret = headers.getOrDefault("x-webhook-secret", "");
        if (!webhookSecret.equals(providedSecret)) {
            return Mono.error(new SecurityException("Invalid static secret"));
        }
        return Mono.empty();
    }

    public Mono<Void> validateHmacSignature(String payload, String signature) {
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
