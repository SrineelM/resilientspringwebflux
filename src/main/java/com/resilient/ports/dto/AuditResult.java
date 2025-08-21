package com.resilient.ports.dto;

import java.time.Instant;

public record AuditResult(String id, String status, String message, Instant timestamp, String correlationId) {
    // Compact constructor for validation and defaults
    public AuditResult {
        if (id == null) id = "-";
        if (status == null) status = "UNKNOWN";
        if (message == null) message = "No message provided";
        if (timestamp == null) timestamp = Instant.now();
    }

    // Convenience constructor matching your original signature
    public AuditResult(String id, String status, String message) {
        this(id, status, message, Instant.now(), null);
    }

    // Static factory methods
    public static AuditResult success(String id, String correlationId, String message) {
        return new AuditResult(id, "SUCCESS", message, Instant.now(), correlationId);
    }

    public static AuditResult failure(String correlationId, String message) {
        return new AuditResult("-", "FAILURE", message, Instant.now(), correlationId);
    }

    public static AuditResult fallback(String message) {
        return new AuditResult("-", "FALLBACK", message, Instant.now(), null);
    }

    public static AuditResult processing(String id, String correlationId) {
        return new AuditResult(id, "PROCESSING", "Audit in progress", Instant.now(), correlationId);
    }

    // Utility methods
    public boolean isSuccess() {
        return "SUCCESS".equals(status) || "OK".equals(status);
    }

    public boolean isFallback() {
        return "FALLBACK".equals(status);
    }

    public boolean isFailure() {
        return "FAILURE".equals(status) || "ERROR".equals(status);
    }
}
