package com.resilient.dto;

import com.resilient.model.User;

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
public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        User.UserStatus status,
        String createdAt,
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
