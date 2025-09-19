package com.resilient.controller;

import io.micrometer.tracing.Tracer;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstration controller for Micrometer Baggage functionality.
 * <p>
 * This controller showcases how to work with Micrometer baggage in a Spring WebFlux application.
 * Baggage allows you to propagate custom key-value pairs across service boundaries and correlate
 * them with logs and traces.
 * <p>
 * Key concepts demonstrated:
 * - Reading baggage values from the current tracing context
 * - Comparing baggage values with incoming HTTP headers
 * - Understanding baggage propagation and correlation
 */
@RestController
public class BaggageDemoController {

    /** Micrometer tracer for accessing baggage from the current tracing context */
    private final Tracer tracer;

    /**
     * Constructor for dependency injection of the Micrometer tracer.
     *
     * @param tracer The Micrometer tracer instance used to access baggage
     */
    public BaggageDemoController(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Demonstrates baggage functionality by showing current baggage values and comparing them with headers.
     * <p>
     * This endpoint accepts optional headers that represent common context information:
     * - X-Correlation-Id: Unique request identifier for tracing across services
     * - X-User-Id: Current user identifier for authorization and auditing
     * - X-Tenant-Id: Multi-tenant application identifier for data isolation
     * <p>
     * The response includes both:
     * - baggage.*: Values retrieved from the current tracing baggage context
     * - header.*: Values directly from the incoming HTTP headers
     * <p>
     * Use this endpoint to understand:
     * - How baggage propagates across service calls
     * - When baggage values are set vs. when they're null
     * - The difference between headers and baggage context
     *
     * @param correlationId Optional correlation ID header
     * @param userId Optional user ID header
     * @param tenantId Optional tenant ID header
     * @return Map containing both baggage and header values for comparison
     */
    @GetMapping(path = "/demo/baggage", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> demo(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        Map<String, Object> out = new HashMap<>();

        // Retrieve values from the current baggage context (if any exist)
        // These values come from the tracing context and may have been set by:
        // - Upstream services that sent W3C Baggage headers
        // - Local filters or services that added baggage to the current span
        // - Configuration that automatically maps certain fields to baggage
        out.put("baggage.correlationId", getBaggageValue("correlationId"));
        out.put("baggage.userId", getBaggageValue("userId"));
        out.put("baggage.tenantId", getBaggageValue("tenantId"));

        // Echo the incoming headers for comparison
        // This shows what the client sent vs. what's available in baggage context
        out.put("header.correlationId", correlationId);
        out.put("header.userId", userId);
        out.put("header.tenantId", tenantId);

        return out;
    }

    /**
     * Safely retrieves a baggage value from the current tracing context.
     * <p>
     * Baggage values are stored in the current tracing span and can be accessed
     * through the Micrometer tracer. If no span is active or the baggage key
     * doesn't exist, this method returns null.
     * <p>
     * This method handles potential exceptions that might occur when:
     * - No tracing context is available
     * - The tracer is not properly configured
     * - Baggage access fails for any other reason
     *
     * @param key The baggage key to retrieve (e.g., "correlationId", "userId")
     * @return The baggage value if present, null otherwise
     */
    private String getBaggageValue(String key) {
        try {
            // Access baggage through the tracer - this will return null if:
            // - No active span exists
            // - The baggage key is not set
            // - Baggage functionality is disabled
            return tracer.getBaggage(key) != null ? tracer.getBaggage(key).get() : null;
        } catch (Throwable t) {
            // Log at debug level since baggage access failures are usually not critical
            // Common causes: tracing disabled, no span context, configuration issues
            return null;
        }
    }
}
