/**
 * Reactive repository for user credentials using Spring Data R2DBC.
 * <p>
 * Provides CRUD and custom queries for user authentication data (e.g., username, password hash).
 * Used by the security layer to load credentials for authentication.
 */
package com.resilient.repository;

import com.resilient.security.UserCredentials;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserCredentialsRepository extends R2dbcRepository<UserCredentials, Long> {
    /**
     * Finds user credentials by username.
     *
     * @param username the username to search for
     * @return a Mono emitting the UserCredentials if found, or empty if not
     */
    Mono<UserCredentials> findByUsername(String username);
}
