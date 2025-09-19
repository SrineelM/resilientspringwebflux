package com.resilient.filter;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
 * WebFilter that maps incoming headers (X-Correlation-Id, X-User-Id, X-Tenant-Id)
 * into OpenTelemetry Baggage so they propagate to downstream services when using
 * W3C Baggage propagation. Keys used: correlationId, userId, tenantId.
 */
@Component
public class BaggageHeaderFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(BaggageHeaderFilter.class);

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var headers = exchange.getRequest().getHeaders();
        String cid = trim(headers.getFirst("X-Correlation-Id"));
        String uid = trim(headers.getFirst("X-User-Id"));
        String tid = trim(headers.getFirst("X-Tenant-Id"));

        // If no values, proceed without creating baggage
        if (!StringUtils.hasText(cid) && !StringUtils.hasText(uid) && !StringUtils.hasText(tid)) {
            return chain.filter(exchange);
        }

        // Build baggage with provided keys
        BaggageBuilder builder = Baggage.builder();
        if (StringUtils.hasText(cid)) builder.put("correlationId", cid);
        if (StringUtils.hasText(uid)) builder.put("userId", uid);
        if (StringUtils.hasText(tid)) builder.put("tenantId", tid);
        Baggage baggage = builder.build();

        return Mono.defer(() -> {
                    Scope scope = null;
                    try {
                        scope = Context.current().with(baggage).makeCurrent();
                    } catch (Throwable t) {
                        log.debug("Failed to set baggage in context: {}", t.toString());
                    }
                    Scope finalScope = scope;
                    return chain.filter(exchange).doFinally(s -> closeQuietly(finalScope));
                })
                .contextWrite(ctx -> {
                    if (StringUtils.hasText(cid)) ctx = ctx.put("correlationId", cid);
                    if (StringUtils.hasText(uid)) ctx = ctx.put("userId", uid);
                    if (StringUtils.hasText(tid)) ctx = ctx.put("tenantId", tid);
                    return ctx;
                });
    }

    private static void closeQuietly(Scope scope) {
        if (scope != null) {
            try {
                scope.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }
}
