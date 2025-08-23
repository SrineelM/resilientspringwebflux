package com.resilient.repository;

import com.resilient.security.UserCredentials;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserCredentialsRepository extends R2dbcRepository<UserCredentials, Long> {
    Mono<UserCredentials> findByUsername(String username);
}
