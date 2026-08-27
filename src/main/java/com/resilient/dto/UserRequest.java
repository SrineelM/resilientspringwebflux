package com.resilient.dto;

import com.resilient.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user creation and update requests.
 *
 * <p>Validates username, email, and full name fields.
 *
 * @param username the user's username (required, 3-50 chars)
 * @param email the user's email (required, valid email)
 * @param fullName the user's full name (required)
 */
@Schema(description = "Request payload for creating or updating a user")
public record UserRequest(
        @Schema(
                        description = "Unique username identifier",
                        example = "johndoe",
                        minLength = 3,
                        maxLength = 50,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Username is required")
                @Size(min = 3, max = 50)
                String username,
        @Schema(
                        description = "User's primary email address",
                        example = "johndoe@example.com",
                        format = "email",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Email is required")
                @Email(message = "Email should be valid")
                String email,
        @Schema(
                        description = "Full name of the user",
                        example = "John Doe",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Full name is required")
                String fullName) {

    /**
     * Converts this request to a User domain object.
     *
     * @return a new User instance
     */
    public User toUser() {
        return User.create(username, email, fullName);
    }
}
