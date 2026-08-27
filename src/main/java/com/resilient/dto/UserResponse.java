package com.resilient.dto;

import com.resilient.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for user responses returned by the API.
 *
 * <p>Encapsulates all user fields exposed to clients.
 *
 * @param id the user ID
 * @param username the user's username
 * @param email the user's email
 * @param fullName the user's full name
 * @param status the user's status
 * @param createdAt creation timestamp (ISO string)
 * @param updatedAt last update timestamp (ISO string)
 */
@Schema(description = "User details response payload")
public record UserResponse(
        @Schema(description = "Unique system identifier for the user", example = "1") Long id,
        @Schema(description = "User's unique username", example = "johndoe") String username,
        @Schema(description = "User's primary email address", example = "johndoe@example.com") String email,
        @Schema(description = "User's full name", example = "John Doe") String fullName,
        @Schema(description = "Current account status", example = "ACTIVE") User.UserStatus status,
        @Schema(description = "Timestamp when the user account was created", example = "2026-08-27T08:00:00")
                String createdAt,
        @Schema(description = "Timestamp when the user account was last updated", example = "2026-08-27T08:30:00")
                String updatedAt) {

    /**
     * Maps a User domain object to a UserResponse DTO.
     *
     * @param user the User domain object
     * @return a UserResponse instance
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.email(),
                user.fullName(),
                user.status(),
                user.createdAt().toString(),
                user.updatedAt().toString());
    }
}
