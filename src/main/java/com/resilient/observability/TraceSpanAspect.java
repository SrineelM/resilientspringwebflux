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

    /**
     * Constructs the aspect with the necessary OpenTelemetry and Micrometer components.
     *
     * @param tracer The OpenTelemetry Tracer used to create new spans.
     * @param meterRegistry The Micrometer MeterRegistry used to create timers.
     */
    public TraceSpanAspect(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    /**
     * An "around" advice that intercepts methods annotated with {@link TraceSpan}.
     * It wraps the method execution in a new OpenTelemetry span and a Micrometer timer.
     *
     * @param pjp The proceeding join point, which represents the intercepted method.
     * @param traceSpan The instance of the {@link TraceSpan} annotation on the method.
     * @return The result of the original method call.
     * @throws Throwable If the original method throws an exception.
     */
    @Around("@annotation(traceSpan)")
    public Object traceAnnotatedMethod(ProceedingJoinPoint pjp, TraceSpan traceSpan) throws Throwable {
        // Get metadata from the intercepted method for naming the span and timer.
        String className = pjp.getSignature().getDeclaringTypeName();
        String methodName = pjp.getSignature().getName();
        String spanName = determineSpanName(pjp, traceSpan);

        // Start a timer sample to measure the execution duration.
        Timer.Sample sample = Timer.start(meterRegistry);
        Span span = null;

        // Create and start a new OpenTelemetry span if a tracer is available.
        if (tracer != null) {
            // A span represents a single operation within a trace.
            span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.INTERNAL).startSpan();
            // Add attributes (tags) to the span for detailed context in tracing UIs.
            span.setAttribute("class.name", className);
            span.setAttribute("method.name", methodName);
            // Optionally record method arguments if configured in the annotation.
            span.setAttribute("method.args", Arrays.toString(pjp.getArgs()));
        }

        Object result = null;
        try {
            result = pjp.proceed();
            if (result instanceof Mono<?> mono) {
                // If the method returns a Mono, instrument it to handle async completion.
                return instrumentMono(mono, sample, span, spanName, className, methodName);
            } else if (result instanceof Flux<?> flux) {
                // If the method returns a Flux, instrument it similarly.
                return instrumentFlux(flux, sample, span, spanName, className, methodName);
            }

            // For synchronous methods, mark the span as successful immediately.
            if (span != null) {
                span.setStatus(StatusCode.OK);
                span.setAttribute("execution.status", "SUCCESS");
            }
            return result;
        } catch (Throwable t) {
            // If an exception is thrown, mark the span as an error and record the exception.
            if (span != null) {
                span.setStatus(StatusCode.ERROR, t.getMessage());
                span.setAttribute("execution.status", "ERROR");
                span.setAttribute("error.message", t.getMessage());
                span.recordException(t);
            }
            log.error("Exception in traced method {}: {}", spanName, t.getMessage());
            throw t;
        } finally {
            // For synchronous methods, the instrumentation is stopped here.
            // For reactive types, this is skipped because `stopInstrumentation` is called
            // in the `doFinally` block of the reactive stream.
            if (!(result instanceof Publisher)) {
                stopInstrumentation(sample, span, className, methodName);
            }
        }
    }

    /**
     * Determines the name for the span.
     * It prioritizes the `value` field of the {@link TraceSpan} annotation. If that is empty,
     * it defaults to a name constructed from the class and method name.
     *
     * @param pjp The join point of the intercepted method.
     * @param annotation The {@link TraceSpan} annotation instance.
     * @return The calculated name for the span.
     */
    private String determineSpanName(ProceedingJoinPoint pjp, TraceSpan annotation) {
        // Use the custom name from the annotation if provided.
        if (!annotation.value().isEmpty()) {
            return annotation.value();
        }
        // Otherwise, fall back to a default name format.
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
                        // On successful completion of the Mono, mark the span as OK.
                        span.setStatus(StatusCode.OK);
                        span.setAttribute("execution.status", "SUCCESS");
                    }
                })
                .doOnError(t -> {
                    if (span != null) {
                        span.setStatus(StatusCode.ERROR, t.getMessage());
                        // On error, mark the span as ERROR and record the exception details.
                        span.setAttribute("execution.status", "ERROR");
                        span.setAttribute("error.message", t.getMessage());
                        span.recordException(t);
                    }
                    log.error("Exception in traced Mono {}: {}", spanName, t.getMessage());
                })
                // `doFinally` is executed on success, error, or cancellation.
                // This is the correct place to stop the timer and end the span for reactive types.
                .doFinally(s -> stopInstrumentation(sample, span, className, methodName));
    }

    /**
     * Instruments Flux return types for tracing and timing. Records success/error status and stops
     * instrumentation when complete.
     */
    private Flux<?> instrumentFlux(
            Flux<?> flux, Timer.Sample sample, Span span, String spanName, String className, String methodName) {

        // For a Flux, `doOnComplete` is used for the success signal, as it fires once when the stream is finished.
        return flux.doOnComplete(() -> {
                    if (span != null) {
                        // Mark the span as OK when the Flux completes successfully.
                        span.setStatus(StatusCode.OK);
                        span.setAttribute("execution.status", "SUCCESS");
                    }
                })
                .doOnError(t -> {
                    if (span != null) {
                        span.setStatus(StatusCode.ERROR, t.getMessage());
                        // On error, mark the span as ERROR and record the exception details.
                        span.setAttribute("execution.status", "ERROR");
                        span.setAttribute("error.message", t.getMessage());
                        span.recordException(t);
                    }
                    log.error("Exception in traced Flux {}: {}", spanName, t.getMessage());
                })
                // `doFinally` ensures that instrumentation is stopped regardless of how the Flux terminates.
                .doFinally(s -> stopInstrumentation(sample, span, className, methodName));
    }

    /**
     * Stops instrumentation and records metrics when execution completes. Ends the tracing span and
     * records execution time in Micrometer.
     */
    private void stopInstrumentation(Timer.Sample sample, Span span, String className, String methodName) {

        // Stop the timer and record the duration. The timer is tagged with class and method names.
        sample.stop(meterRegistry.timer("method.execution", "class", className, "method", methodName));

        if (span != null) {
            // End the span, which makes it available to be exported to a tracing backend like Zipkin or Jaeger.
            span.end();
        }
    }
}
