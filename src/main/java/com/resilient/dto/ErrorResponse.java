// ErrorResponse.java
//
// Standardized error response DTO for REST APIs.
// - Includes error code, message, correlationId, timestamp, and optional details.
// - Use builder pattern for easy construction.

package com.resilient.dto;

import java.time.Instant;

/**
 * Standardized error response DTO for REST APIs. Includes error code, message, correlationId,
 * timestamp, and optional details.
 */
public record ErrorResponse(String code, String message, String correlationId, Instant timestamp, Object details) {}
