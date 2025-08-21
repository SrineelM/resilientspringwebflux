package com.resilient.ports;

import com.resilient.model.User;
import com.resilient.ports.dto.NotificationPreferences;
import com.resilient.ports.dto.NotificationResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserNotificationPortTest {
    static class TestUserNotificationPort implements UserNotificationPort {
        @Override
        public Mono<NotificationResult> sendWelcomeNotification(
                String correlationId, User user, NotificationPreferences prefs) {
            return Mono.just(new NotificationResult("1", true, "ok"));
        }

        @Override
        public Mono<NotificationResult> sendStatusUpdate(
                String correlationId, User user, String status, Map<String, Object> metadata) {
            return Mono.just(new NotificationResult("1", true, "ok"));
        }
    }

    @Test
    void sendWelcomeNotification_happyPath() {
        UserNotificationPort port = new TestUserNotificationPort();
        StepVerifier.create(port.sendWelcomeNotification("corr", null, null))
                .expectNextMatches(r -> r != null)
                .verifyComplete();
    }

    @Test
    void sendStatusUpdate_happyPath() {
        UserNotificationPort port = new TestUserNotificationPort();
        StepVerifier.create(port.sendStatusUpdate("corr", null, "active", Map.of()))
                .expectNextMatches(r -> r != null)
                .verifyComplete();
    }
}
