package com.resilient.security.secrets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Local/dev implementation reading secrets from properties/environment.
 * Supports comma separated list: security.jwt.keys=current,previous1,previous2
 */
@Component
@Profile({"dev","local","test"})
public class LocalSecretProvider implements SecretProvider {

    private final String current;
    private final List<String> previous;

    public LocalSecretProvider(
            @Value("${security.jwt.secret:dev-default-secret-change}") String secret,
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
