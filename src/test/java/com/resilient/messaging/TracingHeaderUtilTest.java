package com.resilient.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

class TracingHeaderUtilTest {
    @Test
    void addsTraceparentWhenMissing() {
        Map<String,String> result = TracingHeaderUtil.ensureTracing(Map.of());
        assertTrue(result.containsKey(TracingHeaderUtil.TRACEPARENT));
        assertTrue(result.get(TracingHeaderUtil.TRACEPARENT).startsWith("00-"));
    }

    @Test
    void preservesExistingTraceparent() {
        Map<String,String> result = TracingHeaderUtil.ensureTracing(Map.of("traceparent","00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"));
        assertEquals("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01", result.get("traceparent"));
    }
}
