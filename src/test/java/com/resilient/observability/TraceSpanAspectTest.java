package com.resilient.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TraceSpanAspectTest {
    @Mock
    Tracer tracer;

    @Mock
    MeterRegistry meterRegistry;

    @Mock
    ProceedingJoinPoint pjp;

    @Mock
    TraceSpan traceSpan;

    @Mock
    Signature signature;

    @Mock
    SpanBuilder spanBuilder;

    @Mock
    Span span;

    TraceSpanAspect aspect;

    @BeforeEach
    void setUp() throws Throwable {
        MockitoAnnotations.openMocks(this);
        aspect = new TraceSpanAspect(tracer, meterRegistry);

        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("TestClass.testMethod()");
        when(pjp.proceed()).thenReturn("result");

        // Mocking the tracer behavior
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
    }

    @Test
    void traceAnnotatedMethod_withAnnotationValue_usesValueAsSpanName() throws Throwable {
        // given
        when(traceSpan.value()).thenReturn("custom-span-name");

        // when
        Object result = aspect.traceAnnotatedMethod(pjp, traceSpan);

        // then
        assertEquals("result", result);

        // Verify tracing logic
        verify(tracer).spanBuilder("custom-span-name");
        verify(spanBuilder).startSpan();
        verify(pjp).proceed();
        verify(span).end();
    }

    @Test
    void traceAnnotatedMethod_withEmptyAnnotationValue_usesMethodSignatureAsSpanName() throws Throwable {
        // given
        when(traceSpan.value()).thenReturn("");

        // when
        Object result = aspect.traceAnnotatedMethod(pjp, traceSpan);

        // then
        assertEquals("result", result);

        // Verify tracing logic
        verify(tracer).spanBuilder("TestClass.testMethod()");
        verify(spanBuilder).startSpan();
        verify(pjp).proceed();
        verify(span).end();
    }
}
