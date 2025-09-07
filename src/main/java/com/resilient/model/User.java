package com.resilient.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Domain model for a user entity.
 *
 * <p>Represents a user in the system, mapped to the "users" table.
 *
 * @param id the user ID (primary key)
 * @param username the user's username
 * @param email the user's email
 * @param fullName the user's full name
 * @param status the user's status (ACTIVE, INACTIVE, SUSPENDED)
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
@Table("users")
public record User(
        @Id Long id,
        @NotBlank(message = "Username is required") @Size(min = 3, max = 50) String username,
        @NotBlank(message = "Email is required")
                @Email(
                        message = "Email should be valid",
                        regexp = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")
                String email,
        @NotBlank(message = "Full name is required") String fullName,
        UserStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt) {
    /** Enum for user status values. */
    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED
    }

    /**
     * Factory method to create a new active user.
     *
     * @param username the username
     * @param email the email
     * @param fullName the full name
     * @return a new User instance
     */
    public static User create(String username, String email, String fullName) {
        LocalDateTime now = LocalDateTime.now();
        return new User(null, username, email, fullName, UserStatus.ACTIVE, now, now);
    }

    /**
     * Returns a copy of this user with a new ID.
     *
     * @param id the new user ID
     * @return a new User instance with the given ID
     */
    public User withId(Long id) {
        return new User(id, username, email, fullName, status, createdAt, updatedAt);
    }

    /**
     * Returns a copy of this user with a new status and updated timestamp.
     *
     * @param status the new user status
     * @return a new User instance with the given status
     */
    public User withStatus(UserStatus status) {
        return new User(id, username, email, fullName, status, createdAt, LocalDateTime.now());
    }
}
