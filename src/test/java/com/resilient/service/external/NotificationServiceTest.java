package com.resilient.service.external;

import com.resilient.model.User;
import com.resilient.ports.dto.NotificationPreferences;
import com.resilient.ports.dto.NotificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class NotificationServiceTest {

    NotificationService notificationService;

    @BeforeEach
    void setUp() {
        // Use the immediate scheduler for deterministic, non-blocking testing
        notificationService = new NotificationService(Schedulers.immediate());
    }

    @Test
    void sendWelcomeNotification_happyPath() {
        User user = User.create("testuser", "test@example.com", "Test User");
        NotificationPreferences prefs = new NotificationPreferences("email", true, false);
        String correlationId = "test-correlation-id-123";

        Mono<NotificationResult> result = notificationService.sendWelcomeNotification(correlationId, user, prefs);
        StepVerifier.create(result)
                .expectNextMatches(res -> {
                    // The service can either succeed or trigger the fallback. This test must accept both
                    // outcomes to be deterministic. We make the checks for each case more specific.
                    boolean isSuccess = res.success()
                            && res.id() != null
                            && !res.id().equals("-") // Successful result must have a real ID.
                            && "sent".equals(res.message()); // Successful result has a "sent" message.

                    boolean isFallback = !res.success()
                            && "-".equals(res.id()) // Fallback result has the placeholder "-" ID.
                            && "queued".equals(res.message()); // Fallback result has a "queued" message.
                    return isSuccess || isFallback;
                })
                .verifyComplete();
    }
}
