package com.resilient.ports;

import com.resilient.model.User;
import com.resilient.ports.dto.NotificationPreferences;
import com.resilient.ports.dto.NotificationResult;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Domain port (interface) for sending notifications to users.
 * <p>
 * This interface defines the contract for notification adapters (e.g., email, SMS, push) in a
 * hexagonal/clean architecture. Implementations may use external services or stubs for testing.
 * <p>
 * Ports decouple business logic from infrastructure, making the application more testable and maintainable.
 */
public interface UserNotificationPort {

    /**
     * Sends a welcome notification to a user.
     *
     * @param correlationId Unique ID for tracing/logging the request
     * @param user The user to notify
     * @param prefs User's notification preferences (e.g., email, SMS)
     * @return a Mono emitting the result of the notification attempt
     */
    Mono<NotificationResult> sendWelcomeNotification(String correlationId, User user, NotificationPreferences prefs);

    /**
     * Sends a status update notification to a user.
     *
     * @param correlationId Unique ID for tracing/logging the request
     * @param user The user to notify
     * @param status The new status to report
     * @param metadata Additional metadata for the notification (optional)
     * @return a Mono emitting the result of the notification attempt
     */
    Mono<NotificationResult> sendStatusUpdate(
            String correlationId, User user, String status, Map<String, Object> metadata);
}
