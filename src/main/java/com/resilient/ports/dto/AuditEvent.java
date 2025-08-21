package com.resilient.ports.dto;

import com.resilient.model.User;
import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        String correlationId, String action, User user, Map<String, Object> context, Instant timestamp, String source) {
    // Compact constructor for validation and defaults
    public AuditEvent {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or empty");
        }
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (timestamp == null) timestamp = Instant.now();
        if (source == null) source = "UNKNOWN";
    }

    // Convenience constructor matching your original signature
    public AuditEvent(String correlationId, String action, User user, Map<String, Object> context) {
        this(correlationId, action, user, context, Instant.now(), "API");
    }

    // Static factory methods
    public static AuditEvent of(String correlationId, String action, User user, Map<String, Object> context) {
        return new AuditEvent(correlationId, action, user, context);
    }

    public static AuditEvent webhook(String correlationId, String action, User user, Map<String, Object> context) {
        return new AuditEvent(correlationId, action, user, context, Instant.now(), "WEBHOOK");
    }

    public static AuditEvent system(String correlationId, String action, User user, Map<String, Object> context) {
        return new AuditEvent(correlationId, action, user, context, Instant.now(), "SYSTEM");
    }
}
