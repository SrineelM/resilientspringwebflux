package com.resilient.security.secrets;

import java.util.List;

/**
 * Abstraction for obtaining sensitive secrets (JWT keys, HMAC secrets, etc.).
 */
public interface SecretProvider {
    /**
     * @return current primary symmetric signing secret (Base64 or raw)
     */
    String currentJwtSecret();

    /**
     * @return list of previous secrets (for rotation) excluding current; may be empty
     */
    List<String> previousJwtSecrets();
}
