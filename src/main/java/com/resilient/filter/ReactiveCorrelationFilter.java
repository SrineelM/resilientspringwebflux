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
 * Reactive WebFilter that ensures a correlation ID is: - Present in requests (generated if missing)
 * - Added to responses for client visibility - Stored in Reactor Context for reactive chains -
 * Added to MDC for logging correlation
 *
 * <p>Notes: - MDC usage is only for logging; correlation propagation is done via Reactor Context. -
 * MDC is cleared after each request to prevent leakage between threads.
 */
@Component
public class ReactiveCorrelationFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveCorrelationFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_CONTEXT = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = extractOrGenerateCorrelationId(exchange);

        // Always expose correlation ID to the client
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        log.debug(
                "Assigned correlationId={} for request {} {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI());

        return chain.filter(exchange)
                // Add to Reactor Context for reactive propagation
                .contextWrite(Context.of(CORRELATION_ID_CONTEXT, correlationId))
                // Wrap for MDC logging support
                .doOnSubscribe(sub -> MDC.put(CORRELATION_ID_CONTEXT, correlationId))
                .doFinally(signal -> MDC.clear());
    }

    private String extractOrGenerateCorrelationId(ServerWebExchange exchange) {
        String upstream = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        return (upstream != null && !upstream.isBlank())
                ? upstream
                : UUID.randomUUID().toString();
    }
}
