package com.resilient.filter;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that maps incoming HTTP headers to Micrometer baggage for cross-service context propagation.
 * <p>
 * This filter demonstrates how to bridge HTTP headers and Micrometer baggage to enable context
 * propagation across distributed services. It extracts common context headers and makes them
 * available both in the tracing baggage (for downstream propagation) and Reactor Context
 * (for local reactive processing).
 * <p>
 * Headers mapped to baggage:
 * - X-Correlation-Id → correlationId (request tracing identifier)
 * - X-User-Id → userId (current user context)
 * - X-Tenant-Id → tenantId (multi-tenant isolation)
 * <p>
 * Benefits of using baggage:
 * - Automatic propagation to downstream HTTP calls via W3C Baggage headers
 * - Correlation with logs through MDC integration (configured in application.yml)
 * - Visibility in distributed traces (Zipkin, Jaeger, etc.)
 * - Access from any part of the application through the tracer
 * <p>
 * Note: This implementation uses deprecated Micrometer APIs and serves as a demonstration.
 * In production, consider using more recent baggage APIs or Spring Cloud Sleuth.
 */
@Component
public class HttpHeaderToBaggageFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(HttpHeaderToBaggageFilter.class);

    private final Tracer tracer;

    public HttpHeaderToBaggageFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var headers = exchange.getRequest().getHeaders();

        return Mono.defer(() -> {
                    // Extract header values for baggage mapping
                    String correlationId = headers.getFirst("X-Correlation-Id");
                    String userId = headers.getFirst("X-User-Id");
                    String tenantId = headers.getFirst("X-Tenant-Id");

                    // Create baggage scopes for the duration of the request
                    // Each scope ensures the baggage value is available for the current thread/span
                    BaggageInScope cidScope = createBaggageScope("correlationId", correlationId);
                    BaggageInScope uidScope = createBaggageScope("userId", userId);
                    BaggageInScope tidScope = createBaggageScope("tenantId", tenantId);

                    return chain.filter(exchange)
                            // Add values to Reactor Context for reactive stream processing
                            .contextWrite(ctx -> {
                                // Priority: header value first, then existing baggage value as fallback
                                ctx = addToContextIfPresent(ctx, "correlationId", correlationId, "correlationId");
                                ctx = addToContextIfPresent(ctx, "userId", userId, "userId");
                                ctx = addToContextIfPresent(ctx, "tenantId", tenantId, "tenantId");
                                return ctx;
                            })
                            // Clean up baggage scopes when request processing completes
                            .doFinally(signal -> {
                                closeQuietly(cidScope);
                                closeQuietly(uidScope);
                                closeQuietly(tidScope);
                            });
                })
                .doOnError(e -> log.debug("Baggage mapping error: {}", e.toString()));
    }

    /**
     * Creates a baggage scope with the given key and value if the value is present.
     *
     * Note: This implementation uses deprecated Micrometer APIs (createBaggage and set methods)
     * as they are still functional and widely used. For production environments, consider
     * migrating to newer baggage APIs when they become stable across all Micrometer versions.
     *
     * @param key the baggage key (should match configuration in application.yml)
     * @param headerValue the header value to set as baggage
     * @return a BaggageInScope that must be closed when no longer needed, or null if value is empty
     */
    @SuppressWarnings("deprecation") // Acknowledged deprecated API usage for compatibility
    private BaggageInScope createBaggageScope(String key, String headerValue) {
        if (!StringUtils.hasText(headerValue) || tracer == null) {
            return null;
        }
        try {
            // Get or create baggage for the key and set the value
            // Using the current approach that works with existing Micrometer versions
            Baggage baggage = tracer.getBaggage(key);
            if (baggage != null) {
                return baggage.makeCurrent(headerValue.trim());
            }

            // Fallback to deprecated API - still functional and widely supported
            baggage = tracer.createBaggage(key);
            baggage.set(headerValue.trim());
            return baggage.makeCurrent();
        } catch (Exception e) {
            log.debug("Failed to create baggage for key '{}': {}", key, e.toString());
            return null;
        }
    }

    /**
     * Adds a value to the Reactor Context if it has text content.
     * This ensures the value is available throughout the reactive stream processing.
     *
     * @param ctx the current reactor context
     * @param contextKey the key to use in the context
     * @param headerValue the header value to add (if present)
     * @param baggageKey the baggage key to check as fallback
     * @return the updated context
     */
    private reactor.util.context.Context addToContextIfPresent(
            reactor.util.context.Context ctx, String contextKey, String headerValue, String baggageKey) {
        if (StringUtils.hasText(headerValue)) {
            return ctx.put(contextKey, headerValue.trim());
        }

        // If no header value, try to get from existing baggage
        try {
            String baggageValue = tracer != null && tracer.getBaggage(baggageKey) != null
                    ? tracer.getBaggage(baggageKey).get()
                    : null;
            if (StringUtils.hasText(baggageValue)) {
                return ctx.put(contextKey, baggageValue);
            }
        } catch (Exception e) {
            log.debug("Could not read baggage value for key '{}': {}", baggageKey, e.toString());
        }

        return ctx;
    }

    /**
     * Safely closes a baggage scope, handling null values and exceptions.
     * This is essential for preventing baggage scope leaks and ensuring proper cleanup.
     *
     * @param scope the baggage scope to close (may be null)
     */
    private void closeQuietly(BaggageInScope scope) {
        if (scope != null) {
            try {
                scope.close();
            } catch (Exception e) {
                log.debug("Error closing baggage scope: {}", e.toString());
            }
        }
    }
}
