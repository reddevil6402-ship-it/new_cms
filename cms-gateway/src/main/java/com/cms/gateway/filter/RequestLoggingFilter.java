package com.cms.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Logs every request passing through the gateway with method, path, status, and duration.
 * Masks the Authorization header value — never logs tokens.
 *
 * <p>Log format: {@code [GATEWAY] METHOD /path → STATUS in XXms}
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant start  = Instant.now();
        String method  = exchange.getRequest().getMethod().name();
        String path    = exchange.getRequest().getPath().value();
        String hasAuth = exchange.getRequest().getHeaders().containsKey("Authorization")
                ? "[JWT]" : "[no-auth]";

        log.debug("[GATEWAY] → {} {} {}", method, path, hasAuth);

        return chain.filter(exchange).doFinally(signal -> {
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
            long ms = Duration.between(start, Instant.now()).toMillis();
            log.debug("[GATEWAY] ← {} {} → {} in {}ms", method, path, status, ms);
        });
    }
}
