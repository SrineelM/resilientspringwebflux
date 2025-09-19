package com.resilient.messaging;

import java.util.Map;
import java.util.UUID;

/**
 * Utility class for handling distributed tracing headers (W3C traceparent).
 * <p>
 * Provides methods to ensure that a traceparent header is present in outgoing message headers,
 * generating a new one if absent. This enables end-to-end request tracing across microservices
 * and messaging systems.
 *
 * <p>See: https://www.w3.org/TR/trace-context/
 */
public final class TracingHeaderUtil {
    /** The W3C traceparent header key. */
    public static final String TRACEPARENT = "traceparent";
    /** The W3C tracestate header key (not used here, but reserved for future use). */
    public static final String TRACESTATE = "tracestate";

    /**
     * Private constructor to prevent instantiation (utility class).
     */
    private TracingHeaderUtil() {}

    /**
     * Ensures that the provided headers map contains a W3C traceparent header.
     * <p>
     * If the input map is null or empty, returns a new map with a generated traceparent.
     * If the map is missing the traceparent key, returns a copy with a new traceparent added.
     * If already present, returns the original map.
     *
     * @param headers The original headers map (may be null or empty)
     * @return A map guaranteed to contain a traceparent header
     */
    public static Map<String, String> ensureTracing(Map<String, String> headers) {
        // If no headers, create a new map with just the traceparent
        if (headers == null || headers.isEmpty()) {
            return Map.of(TRACEPARENT, newTraceParent());
        }
        // If traceparent is missing, copy and add it
        if (!headers.containsKey(TRACEPARENT)) {
            java.util.HashMap<String, String> copy = new java.util.HashMap<>(headers);
            copy.put(TRACEPARENT, newTraceParent());
            return copy;
        }
        // Already present, return as-is
        return headers;
    }

    /**
     * Generates a new W3C traceparent header value.
     * <p>
     * Format: 00-<trace-id>-<span-id>-01
     * - trace-id: 16 bytes (32 hex chars)
     * - span-id: 8 bytes (16 hex chars)
     * - 01: sampled flag (always sampled here)
     *
     * @return A valid traceparent header value
     */
    private static String newTraceParent() {
        // Generate a 16-byte (32 hex chars) trace ID
        String traceId = UUID.randomUUID().toString().replace("-", "");
        // Generate an 8-byte (16 hex chars) span ID
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // Return the W3C traceparent format: version-traceid-spanid-flags
        return "00-" + traceId.substring(0, 32) + "-" + spanId + "-01"; // sampled
    }
}
