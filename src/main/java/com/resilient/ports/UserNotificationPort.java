package com.resilient.ports;

import com.resilient.model.User;
import com.resilient.ports.dto.NotificationPreferences;
import com.resilient.ports.dto.NotificationResult;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Domain-specific notification port for user notifications. */
public interface UserNotificationPort {
    Mono<NotificationResult> sendWelcomeNotification(String correlationId, User user, NotificationPreferences prefs);

    Mono<NotificationResult> sendStatusUpdate(
            String correlationId, User user, String status, Map<String, Object> metadata);
}
