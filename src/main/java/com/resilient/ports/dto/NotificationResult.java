package com.resilient.ports.dto;

/**
 * Represents the result of a notification attempt.
 *
 * @param id The unique identifier of the notification if successful, otherwise null.
 * @param success A flag indicating whether the notification was sent successfully.
 * @param message A descriptive message, e.g., "sent" on success or an error message on failure.
 */
public record NotificationResult(String id, boolean success, String message) {
    /**
     * Creates a successful notification result.
     *
     * @param id The unique identifier of the successful notification.
     * @return A new {@link NotificationResult} instance for a success case.
     */
    public static NotificationResult ok(String id) {
        return new NotificationResult(id, true, "sent");
    }

    /**
     * Creates a failed notification result.
     *
     * @param message The error message describing the failure.
     * @return A new {@link NotificationResult} instance for a failure case.
     */
    public static NotificationResult failed(String message) {
        return new NotificationResult("-", false, message);
    }
}
