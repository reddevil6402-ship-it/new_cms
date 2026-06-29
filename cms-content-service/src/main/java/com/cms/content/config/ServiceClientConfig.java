package com.cms.content.config;

import com.cms.common.constants.CmsHeaders;
import com.cms.common.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

@Configuration
public class ServiceClientConfig {

    @Value("${cms.services.schema-url}")
    private String schemaUrl;

    @Value("${cms.services.workflow-url}")
    private String workflowUrl;

    @Bean
    public RestClient schemaRestClient() {
        return RestClient.builder()
                .baseUrl(schemaUrl)
                .requestInterceptor(authForwardingInterceptor())
                .build();
    }

    @Bean
    public RestClient workflowRestClient() {
        return RestClient.builder()
                .baseUrl(workflowUrl)
                .requestInterceptor(authForwardingInterceptor())
                .build();
    }

    /**
     * Interceptor to automatically forward the current JWT token and X-Tenant-Id header
     * from the security and tenant context to downstream microservice calls.
     */
    private ClientHttpRequestInterceptor authForwardingInterceptor() {
        return (request, body, execution) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                String tokenValue = jwtAuth.getToken().getTokenValue();
                request.getHeaders().setBearerAuth(tokenValue);
                
                // Also forward current User ID if present
                String userId = jwtAuth.getName();
                if (userId != null && !userId.isBlank()) {
                    request.getHeaders().set(CmsHeaders.X_USER_ID, userId);
                }
                
                // Also forward User Email if present in claims
                String email = jwtAuth.getToken().getClaimAsString("email");
                if (email != null && !email.isBlank()) {
                    request.getHeaders().set("X-User-Email", email);
                }
            }

            // Forward X-Tenant-Id from TenantContext filter
            String tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                request.getHeaders().set(CmsHeaders.X_TENANT_ID, tenantId);
            }

            return execution.execute(request, body);
        };
    }
}
