package com.cms.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;

/**
 * Gateway WebFlux security configuration.
 *
 * <p><strong>Public routes:</strong> auth endpoints and dev UI — no JWT required.
 * <p><strong>All other routes:</strong> require a valid RS256 JWT issued by cms-iam-service.
 *
 * <p>JWT validation uses the JWKS endpoint on iam-service.
 * The key is fetched once and cached — no iam-service call on every request.
 * Cache is refreshed automatically when a new {@code kid} is encountered.
 *
 * <p>The gateway does NOT issue tokens — it only validates and forwards.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ROUTES = {
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/.well-known/jwks.json",
        "/actuator/health",
        "/actuator/health/readiness",
        "/actuator/info",
        "/dev/**"        // Dev test UI — remove before production
    };

    @Value("${cms.iam.jwks-uri}")
    private String jwksUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_ROUTES).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
                )
                .exceptionHandling(ex -> ex
                        // Return clean 401/403 JSON — not redirect to login page
                        .authenticationEntryPoint(
                                new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .build();
    }

    /**
     * Reactive JWT decoder backed by iam-service JWKS endpoint.
     *
     * <p>NimbusReactiveJwtDecoder fetches the public key from the JWKS URI on first use
     * and caches it. It automatically re-fetches when a JWT with an unknown {@code kid}
     * is encountered — supports future key rotation without gateway restart.
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
