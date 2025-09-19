package com.resilient.filter;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that extracts selected headers and places them into Micrometer Baggage (W3C Baggage),
 * Reactor Context, and MDC via Micrometer correlation configuration.
 *
 * Requires: micrometer-tracing-bridge-otel dependency and management.tracing.baggage.* properties.
 */
public class BaggageWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(BaggageWebFilter.class);

    private final Tracer tracer;

    public BaggageWebFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var headers = exchange.getRequest().getHeaders();

        return Mono.defer(() -> {
                    BaggageInScope cidScope = createBaggageScope("correlationId", headers.getFirst("X-Correlation-Id"));
                    BaggageInScope uidScope = createBaggageScope("userId", headers.getFirst("X-User-Id"));
                    BaggageInScope tidScope = createBaggageScope("tenantId", headers.getFirst("X-Tenant-Id"));

                    return chain.filter(exchange)
                            .contextWrite(context -> {
                                String correlationId = firstNonBlank(
                                        headers.getFirst("X-Correlation-Id"), getBaggageValue("correlationId"));
                                if (StringUtils.hasText(correlationId)) {
                                    context = context.put("correlationId", correlationId);
                                }
                                String userId = firstNonBlank(headers.getFirst("X-User-Id"), getBaggageValue("userId"));
                                if (StringUtils.hasText(userId)) {
                                    context = context.put("userId", userId);
                                }
                                String tenantId =
                                        firstNonBlank(headers.getFirst("X-Tenant-Id"), getBaggageValue("tenantId"));
                                if (StringUtils.hasText(tenantId)) {
                                    context = context.put("tenantId", tenantId);
                                }
                                return context;
                            })
                            .doFinally(s -> {
                                closeQuietly(cidScope);
                                closeQuietly(uidScope);
                                closeQuietly(tidScope);
                            });
                })
                .doOnError(e -> log.debug("Baggage filter error: {}", e.toString()));
    }

    @SuppressWarnings("deprecation") // Acknowledged deprecated API usage for compatibility
    private BaggageInScope createBaggageScope(String key, String value) {
        if (!StringUtils.hasText(value) || tracer == null) return null;
        try {
            // Create baggage and return a scope for proper lifecycle management
            io.micrometer.tracing.Baggage baggage = tracer.createBaggage(key);
            baggage.set(value.trim());
            return baggage.makeCurrent();
        } catch (Throwable t) {
            log.debug("Unable to create baggage key {}: {}", key, t.toString());
            return null;
        }
    }

    private String getBaggageValue(String key) {
        try {
            return tracer != null && tracer.getBaggage(key) != null
                    ? tracer.getBaggage(key).get()
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void closeQuietly(BaggageInScope scope) {
        if (scope != null) {
            try {
                scope.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a;
        return StringUtils.hasText(b) ? b : null;
    }
}
