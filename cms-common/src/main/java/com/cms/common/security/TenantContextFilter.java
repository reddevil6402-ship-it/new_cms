package com.cms.common.security;

import com.cms.common.constants.CmsHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that extracts the tenant ID from the {@code X-Tenant-Id} request header
 * and stores it in {@link TenantContext} for the duration of the request.
 *
 * <p><strong>When does X-Tenant-Id arrive?</strong>
 * The API Gateway (Spring Cloud Gateway) validates the JWT and forwards
 * {@code X-Tenant-Id} and {@code X-User-Id} as synthetic headers to downstream services.
 * Downstream services must NOT re-parse the JWT — they trust these Gateway-injected headers.
 *
 * <p><strong>Thread safety:</strong>
 * The {@code finally} block unconditionally calls {@link TenantContext#clear()} to prevent
 * the tenant ID from leaking to the next request on the same pooled servlet thread.
 * This is the primary safety mechanism — do NOT remove it.
 *
 * <p><strong>Health checks and internal routes:</strong>
 * If no {@code X-Tenant-Id} header is present (e.g., {@code /actuator/health}),
 * the filter is a no-op. {@link TenantContext#getCurrentTenantId()} returns {@code null},
 * and {@link TenantAwareDataSource} skips the {@code SET LOCAL} call.
 *
 * <p>Registered with order 1 via {@link com.cms.common.autoconfigure.CmsCommonAutoConfiguration}
 * to run before Spring Security filters.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = request.getHeader(CmsHeaders.X_TENANT_ID);
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setCurrentTenantId(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: always clear — prevents ThreadLocal leakage across pooled servlet threads
            TenantContext.clear();
        }
    }
}
