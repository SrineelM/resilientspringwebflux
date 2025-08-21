package com.resilient.observability;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class TraceSpanTest {
    @TraceSpan(value = "testSpan", operation = "testOp", tags = "foo:bar", recordArgs = true)
    void annotatedMethod() {}

    @Test
    void annotation_happyPath() throws Exception {
        Method method = this.getClass().getDeclaredMethod("annotatedMethod");
        TraceSpan traceSpan = method.getAnnotation(TraceSpan.class);
        assertNotNull(traceSpan);
        assertEquals("testSpan", traceSpan.value());
        assertEquals("testOp", traceSpan.operation());
        assertEquals("foo:bar", traceSpan.tags());
        assertTrue(traceSpan.recordArgs());
    }
}
