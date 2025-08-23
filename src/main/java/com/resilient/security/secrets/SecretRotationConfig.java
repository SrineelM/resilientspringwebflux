package com.resilient.security.secrets;

import com.resilient.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Periodically refreshes JWT secrets from the active SecretProvider (if any).
 * This provides lightweight key rotation support when the underlying source updates
 * secrets out-of-band (e.g. environment reload + container restart not yet happened,
 * or a dynamic provider that changes values in-place).
 */
@Configuration
@EnableScheduling
public class SecretRotationConfig {

    private static final Logger log = LoggerFactory.getLogger(SecretRotationConfig.class);
    private final JwtUtil jwtUtil;

    public SecretRotationConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // Every 15 minutes; adjust via property if needed later
    @Scheduled(fixedDelayString = "${security.jwt.rotation-refresh-ms:900000}")
    public void refresh() {
        try {
            jwtUtil.refreshSecrets();
            log.debug("Refreshed JWT signing secrets (rotation check)");
        } catch (Exception e) {
            log.warn("JWT secret refresh failed: {}", e.getMessage());
        }
    }
}
