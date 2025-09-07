package com.resilient.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * A reactive {@link WebFilter} that manages a correlation ID for every incoming request.
 *
 * <p>This filter intercepts each HTTP request to ensure a unique correlation ID is present. If the
 * incoming request already has an {@code X-Correlation-ID} header, it is used; otherwise, a new
 * UUID is generated.
 *
 * <p>The correlation ID is then:
 * <ol>
 *   <li>Added to the response headers, making it visible to the client for tracing.</li>
 *   <li>Injected into the Project Reactor {@link Context}, allowing it to be accessed throughout
 *       the reactive stream (e.g., in services and controllers).</li>
 *   <li>Placed into the SLF4J {@link MDC} (Mapped Diagnostic Context) to automatically include it
 *       in log statements for the duration of the request processing.</li>
 * </ol>
 *
 * <p>The MDC is cleared in a {@code doFinally} block to prevent the correlation ID from leaking
 * into other requests that might be processed by the same thread.
 */
@Component
public class ReactiveCorrelationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveCorrelationFilter.class);

    /** The HTTP header name used for the correlation ID. */
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** The key used to store the correlation ID in the Reactor Context. */
    public static final String CORRELATION_ID_CONTEXT = "correlationId";

    /**
     * Intercepts the request to apply the correlation ID logic.
     *
     * @param exchange The current server web exchange, containing the request and response.
     * @param chain The filter chain to pass control to the next filter.
     * @return A {@link Mono} that completes when the request has been fully handled.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. Get the correlation ID from the request header or generate a new one.
        String correlationId = extractOrGenerateCorrelationId(exchange);

        // 2. Add the correlation ID to the response headers so the client can see it.
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        log.debug(
                "Assigned correlationId={} for request {} {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI());

        // 3. Continue the filter chain with the correlation ID in the reactive context and MDC.
        return chain.filter(exchange)
                // Add the correlation ID to the Reactor Context for downstream reactive components.
                .contextWrite(Context.of(CORRELATION_ID_CONTEXT, correlationId))
                // Before the subscription happens, put the ID into the MDC for logging.
                .doOnSubscribe(sub -> MDC.put(CORRELATION_ID_CONTEXT, correlationId))
                // After the reactive stream terminates (completes, errors, or is cancelled), clear the MDC.
                .doFinally(signal -> MDC.clear());
    }

    /**
     * Extracts the correlation ID from the {@code X-Correlation-ID} header of the request. If the
     * header is missing or blank, a new UUID is generated.
     *
     * @param exchange The current server web exchange.
     * @return The existing or newly generated correlation ID.
     */
    private String extractOrGenerateCorrelationId(ServerWebExchange exchange) {
        String upstream = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        // Use the upstream ID if it's present and not blank, otherwise generate a new one.
        return (upstream != null && !upstream.isBlank())
                ? upstream
                : UUID.randomUUID().toString();
    }
}
