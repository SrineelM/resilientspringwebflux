package com.resilient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Standardized error response DTO for REST APIs.
 * Includes error code, message, correlationId, timestamp, and optional details.
 */
@Schema(description = "Standardized structured error response payload")
public record ErrorResponse(
        @Schema(description = "Machine-readable error classification code", example = "VALIDATION_FAILED") String code,
        @Schema(
                        description = "Human-readable explanation of the error",
                        example = "The request payload failed validation")
                String message,
        @Schema(
                        description = "Distributed trace correlation ID for log tracing",
                        example = "c0a80101-8c4d-4b92-9e23-283948123abc")
                String correlationId,
        @Schema(description = "Timestamp when the error occurred", example = "2026-08-27T08:20:00Z") Instant timestamp,
        @Schema(description = "Optional additional diagnostic details or field error map", nullable = true)
                Object details) {}
