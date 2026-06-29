package com.cms.gateway.filter;

import com.cms.common.constants.CmsHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

/**
 * Global filter that runs on every request after Spring Security authentication.
 *
 * <p>For authenticated requests:
 * <ol>
 *   <li>Validates that the tenant is ACTIVE (rejects with 403 if not)</li>
 *   <li>Extracts JWT claims and injects them as HTTP headers for downstream services:
 *       <ul>
 *         <li>{@code X-Tenant-Id}    — used by TenantContextFilter for RLS</li>
 *         <li>{@code X-User-Id}      — used by services for auditing</li>
 *         <li>{@code X-User-Email}   — used by services for audit trails</li>
 *         <li>{@code X-Tenant-Code}  — convenience, avoids DB lookup downstream</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>For unauthenticated requests (public routes): passes through unchanged.
 *
 * <p>The Authorization header is kept so downstream services can also validate the JWT
 * if they choose to (defense-in-depth). It is NOT stripped at the gateway.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtForwardingFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .flatMap(authToken -> {
                    Jwt jwt = authToken.getToken();

                    // Tenant status gate — checked on every request, not just at login
                    String tenantStatus = jwt.getClaimAsString("tenantStatus");
                    if (!"ACTIVE".equals(tenantStatus)) {
                        log.warn("Blocked request — tenant status is '{}' for tenant '{}'",
                                tenantStatus, jwt.getClaimAsString("tenantCode"));
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }

                    // Inject downstream headers from JWT claims
                    String tenantId   = jwt.getClaimAsString("tenantId");
                    String tenantCode = jwt.getClaimAsString("tenantCode");
                    String userId     = jwt.getSubject();
                    String email      = jwt.getClaimAsString("email");

                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r
                                    .header(CmsHeaders.X_TENANT_ID,   nullSafe(tenantId))
                                    .header(CmsHeaders.X_USER_ID,     nullSafe(userId))
                                    .header("X-User-Email",           nullSafe(email))
                                    .header(CmsHeaders.X_TENANT_CODE, nullSafe(tenantCode))
                            )
                            .build();

                    return chain.filter(mutated);
                })
                // No JWT principal (public route) — pass through unchanged
                .switchIfEmpty(chain.filter(exchange));
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
