package com.resilient.messaging;

import java.util.Map;
import java.util.UUID;

/** Utility to build W3C traceparent header when absent and merge tracing headers. */
public final class TracingHeaderUtil {
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    private TracingHeaderUtil() {}

    public static Map<String,String> ensureTracing(Map<String,String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of(TRACEPARENT, newTraceParent());
        }
        if (!headers.containsKey(TRACEPARENT)) {
            // copy & add
            java.util.HashMap<String,String> copy = new java.util.HashMap<>(headers);
            copy.put(TRACEPARENT, newTraceParent());
            return copy;
        }
        return headers;
    }

    private static String newTraceParent() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0,16);
        return "00-" + traceId.substring(0,32) + "-" + spanId + "-01"; // sampled
    }
}