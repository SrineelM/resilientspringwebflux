package com.resilient.messaging;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TracingHeaderUtilSmokeTest {
    @Test
    void ensureTracingAdds() {
        Map<String, String> out = TracingHeaderUtil.ensureTracing(Map.of());
        assertTrue(out.containsKey(TracingHeaderUtil.TRACEPARENT));
    }
}
