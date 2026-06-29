package com.cms.common.autoconfigure;

import com.cms.common.exception.GlobalExceptionHandler;
import com.cms.common.security.TenantContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Spring Boot auto-configuration for cms-common.
 *
 * <p>Automatically registered in all consuming Spring Boot services via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * No annotation or import is required in the consuming service.
 *
 * <h2>What this registers</h2>
 * <ul>
 *   <li>{@link TenantContextFilter} — registered as a servlet filter at order 1,
 *       before Spring Security filters. Extracts {@code X-Tenant-Id} header into
 *       {@link com.cms.common.security.TenantContext}.</li>
 *   <li>{@link GlobalExceptionHandler} — registered as a {@code @RestControllerAdvice}
 *       that maps {@link com.cms.common.exception.CmsException} and validation errors
 *       to the standard {@link com.cms.common.dto.ApiResponse} envelope.</li>
 * </ul>
 *
 * <h2>What this does NOT register</h2>
 * <ul>
 *   <li>{@link com.cms.common.security.TenantAwareDataSource} — each service
 *       configures its own {@code DataSourceConfig} bean to wrap HikariCP.
 *       This keeps DataSource configuration explicit and per-service.</li>
 *   <li>{@link com.cms.common.security.CmsJwtAuthenticationConverter} — wired
 *       explicitly in each service's {@code SecurityConfig} to keep security
 *       configuration visible and auditable.</li>
 * </ul>
 *
 * <h2>Overriding defaults</h2>
 * <p>Both beans use {@link ConditionalOnMissingBean} — if a consuming service
 * declares its own bean of the same type, the default is not registered.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CmsCommonAutoConfiguration {

    /**
     * Registers {@link TenantContextFilter} as a servlet filter at order 1.
     * Runs before Spring Security's filter chain (which starts at order ~100).
     */
    @Bean
    @ConditionalOnMissingBean(TenantContextFilter.class)
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration() {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);  // order 1 — first in chain
        registration.addUrlPatterns("/*");
        registration.setName("tenantContextFilter");
        return registration;
    }

    /**
     * Registers the shared {@link GlobalExceptionHandler} as a controller advice.
     * Services that need custom exception handling can declare their own
     * {@code @RestControllerAdvice} — Spring picks the most specific handler first.
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
