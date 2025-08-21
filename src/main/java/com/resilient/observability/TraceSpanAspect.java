package com.resilient.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TraceSpanAspect instruments service and controller methods for distributed tracing and timing.
 *
 * <p>This aspect uses OpenTelemetry for distributed tracing and Micrometer for metrics. It wraps
 * method execution in a tracing span and records execution time, supporting both reactive
 * (Mono/Flux) and non-reactive methods. Tracing and metrics are exposed to Zipkin, Prometheus, and
 * Grafana.
 */
@Aspect
@Component
public class TraceSpanAspect {
    private static final Logger log = LoggerFactory.getLogger(TraceSpanAspect.class);
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public TraceSpanAspect(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(traceSpan)")
    public Object traceAnnotatedMethod(ProceedingJoinPoint pjp, TraceSpan traceSpan) throws Throwable {
        String className = pjp.getSignature().getDeclaringTypeName();
        String methodName = pjp.getSignature().getName();
        String spanName = determineSpanName(pjp, traceSpan);

        Timer.Sample sample = Timer.start(meterRegistry);
        Span span = null;
        if (tracer != null) {
            span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.INTERNAL).startSpan();
            span.setAttribute("class.name", className);
            span.setAttribute("method.name", methodName);
            span.setAttribute("method.args", Arrays.toString(pjp.getArgs()));
        }

        Object result = null;
        try {
            result = pjp.proceed();
            if (result instanceof Mono<?> mono) {
                return instrumentMono(mono, sample, span, spanName, className, methodName);
            } else if (result instanceof Flux<?> flux) {
                return instrumentFlux(flux, sample, span, spanName, className, methodName);
            }

            if (span != null) {
                span.setStatus(StatusCode.OK);
                span.setAttribute("execution.status", "SUCCESS");
            }
            return result;
        } catch (Throwable t) {
            if (span != null) {
                span.setStatus(StatusCode.ERROR, t.getMessage());
                span.setAttribute("execution.status", "ERROR");
                span.setAttribute("error.message", t.getMessage());
                span.recordException(t);
            }
            log.error("Tracing error in {}: {}", spanName, t.getMessage());
            throw t;
        } finally {
            if (!(result instanceof Publisher)) {
                stopInstrumentation(sample, span, className, methodName);
            }
        }
    }

    // executeWithTracing removed - simplified to single traceAnnotatedMethod

    private String determineSpanName(ProceedingJoinPoint pjp, TraceSpan annotation) {
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        return pjp.getSignature().getDeclaringTypeName() + "."
                + pjp.getSignature().getName();
    }

    /**
     * Instruments Mono return types for tracing and timing. Records success/error status and stops
     * instrumentation when complete.
     */
    private Mono<?> instrumentMono(
            Mono<?> mono, Timer.Sample sample, Span span, String spanName, String className, String methodName) {

        return mono.doOnSuccess(o -> {
                    if (span != null) {
                        span.setStatus(StatusCode.OK);
                        span.setAttribute("execution.status", "SUCCESS");
                    }
                })
                .doOnError(t -> {
                    if (span != null) {
                        span.setStatus(StatusCode.ERROR, t.getMessage());
                        span.setAttribute("execution.status", "ERROR");
                        span.setAttribute("error.message", t.getMessage());
                        span.recordException(t);
                    }
                    log.error("Tracing error in {}: {}", spanName, t.getMessage());
                })
                .doFinally(s -> stopInstrumentation(sample, span, className, methodName));
    }

    /**
     * Instruments Flux return types for tracing and timing. Records success/error status and stops
     * instrumentation when complete.
     */
    private Flux<?> instrumentFlux(
            Flux<?> flux, Timer.Sample sample, Span span, String spanName, String className, String methodName) {

        return flux.doOnComplete(() -> {
                    if (span != null) {
                        span.setStatus(StatusCode.OK);
                        span.setAttribute("execution.status", "SUCCESS");
                    }
                })
                .doOnError(t -> {
                    if (span != null) {
                        span.setStatus(StatusCode.ERROR, t.getMessage());
                        span.setAttribute("execution.status", "ERROR");
                        span.setAttribute("error.message", t.getMessage());
                        span.recordException(t);
                    }
                    log.error("Tracing error in {}: {}", spanName, t.getMessage());
                })
                .doFinally(s -> stopInstrumentation(sample, span, className, methodName));
    }

    /**
     * Stops instrumentation and records metrics when execution completes. Ends the tracing span and
     * records execution time in Micrometer.
     */
    private void stopInstrumentation(Timer.Sample sample, Span span, String className, String methodName) {

        sample.stop(meterRegistry.timer("method.execution", "class", className, "method", methodName));

        if (span != null) {
            span.setAttribute("execution.endTime", System.currentTimeMillis());
            span.end();
        }
    }
}
