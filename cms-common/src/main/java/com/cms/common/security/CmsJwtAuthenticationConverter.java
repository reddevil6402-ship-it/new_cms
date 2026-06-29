package com.cms.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;

/**
 * Converts a validated {@link Jwt} into a Spring Security {@link AbstractAuthenticationToken}
 * by extracting the CMS-specific {@code permissions[]} claim as {@link GrantedAuthority} objects.
 *
 * <h2>Why custom converter?</h2>
 * <p>Spring Security's default JWT converter reads {@code scope} or {@code scp} claims.
 * The CMS JWT uses a custom {@code permissions} claim with values like
 * {@code "content:CREATE:OWN"} and {@code "schema:READ:ALL"}. This converter maps
 * those strings to {@link SimpleGrantedAuthority} instances so that
 * {@code @PreAuthorize("hasAuthority('content:CREATE:OWN')")} works correctly.
 *
 * <h2>Usage in each service's SecurityConfig</h2>
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
 *     http.oauth2ResourceServer(oauth2 -> oauth2
 *         .jwt(jwt -> jwt.jwtAuthenticationConverter(new CmsJwtAuthenticationConverter()))
 *     );
 *     return http.build();
 * }
 * }</pre>
 *
 * <p>This class is NOT a Spring bean and is NOT auto-configured — services instantiate
 * it explicitly in their {@code SecurityConfig} to keep security wiring explicit and visible.
 */
public class CmsJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String PERMISSIONS_CLAIM = "permissions";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractPermissions(jwt);
        // principal name = userId (the 'sub' claim holds the user UUID)
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    /**
     * Extracts the {@code permissions} array from the JWT and converts each entry
     * to a {@link SimpleGrantedAuthority}.
     *
     * <p>Returns an empty list if the claim is absent (e.g., service accounts
     * with minimal claims). Never returns null.
     */
    private Collection<GrantedAuthority> extractPermissions(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList(PERMISSIONS_CLAIM);
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .map(authority -> (GrantedAuthority) authority)
                .toList();
    }
}
