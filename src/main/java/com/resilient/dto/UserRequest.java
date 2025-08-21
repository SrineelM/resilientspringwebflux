package com.resilient.dto;

import com.resilient.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user creation requests.
 *
 * <p>Validates username, email, and full name fields.
 *
 * @param username the user's username (required, 3-50 chars)
 * @param email the user's email (required, valid email)
 * @param fullName the user's full name (required)
 */
public record UserRequest(
        @NotBlank(message = "Username is required") @Size(min = 3, max = 50) String username,
        @NotBlank(message = "Email is required") @Email(message = "Email should be valid") String email,
        @NotBlank(message = "Full name is required") String fullName) {
    /**
     * Converts this request to a User domain object.
     *
     * @return a new User instance
     */
    public User toUser() {
        return User.create(username, email, fullName);
    }
}
