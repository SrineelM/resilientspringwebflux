package com.resilient.security.secrets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production oriented secret provider that reads secrets from externalized configuration
 * (environment variables / config server / container secrets mounted as env vars).
 *
 * Active for non-dev profiles (anything except local/dev/test). In production you should
 * ensure values are injected via environment variables or a secrets manager that maps
 * into Spring config properties (e.g. Kubernetes secrets + downward API, docker secrets,
 * AWS/GCP secret integrations, etc.).
 *
 * Supports either a single secret (security.jwt.secret) or a comma-separated list of
 * rotating secrets (security.jwt.keys) where the first element is the current active key
 * and subsequent elements are still accepted for signature verification during their
 * grace period.
 */
@Component
@Profile("!local & !dev & !test")
public class EnvironmentSecretProvider implements SecretProvider {

    private final String current;
    private final List<String> previous;

    public EnvironmentSecretProvider(
            @Value("${security.jwt.secret:change-me-prod}") String secret,
            @Value("${security.jwt.keys:}") String keys) {
        if (keys != null && !keys.isBlank()) {
            List<String> list = Arrays.stream(keys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (!list.isEmpty()) {
                this.current = list.get(0);
                this.previous = list.size() > 1 ? list.subList(1, list.size()) : Collections.emptyList();
            } else {
                this.current = secret;
                this.previous = Collections.emptyList();
            }
        } else {
            this.current = secret;
            this.previous = Collections.emptyList();
        }
    }

    @Override
    public String currentJwtSecret() {
        return current;
    }

    @Override
    public List<String> previousJwtSecrets() {
        return previous;
    }
}
