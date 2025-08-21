/** Deletes a user by ID. */
package com.resilient.repository;

import com.resilient.model.User;
import java.time.LocalDateTime;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for User entities using Spring Data R2DBC.
 *
 * <p>Provides CRUD and custom reactive queries for managing users.
 */
@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {

    Mono<User> findByUsername(String username);

    Mono<User> findByEmail(String email);

    Flux<User> findByStatus(User.UserStatus status);

    @Query("SELECT * FROM users WHERE username LIKE :pattern OR email LIKE :pattern")
    Flux<User> findByUsernameOrEmailContaining(@Param("pattern") String pattern);

    @Query("SELECT * FROM users WHERE email = :email AND deleted_at IS NULL")
    Mono<User> findByEmailAndNotDeleted(@Param("email") String email);

    @Query("SELECT * FROM users WHERE created_at >= :since ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<User> findRecentUsers(
            @Param("since") LocalDateTime since, @Param("limit") int limit, @Param("offset") int offset);

    @org.springframework.data.r2dbc.repository.Modifying
    @Query("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
    Mono<Integer> softDeleteById(@Param("id") Long id);

    Mono<Boolean> existsByUsername(String username);

    Mono<Boolean> existsByEmail(String email);

    Mono<Void> deleteById(Long id);
}
