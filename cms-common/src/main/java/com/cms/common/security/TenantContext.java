package com.cms.common.security;

/**
 * ThreadLocal holder for the current request's tenant ID.
 *
 * <p><strong>Lifecycle:</strong>
 * <ol>
 *   <li>{@link TenantContextFilter} sets the tenant ID at the start of each request
 *       by reading the {@code X-Tenant-Id} header forwarded by the API Gateway.</li>
 *   <li>{@link TenantAwareDataSource} reads it on every connection checkout to execute
 *       {@code SET LOCAL app.current_tenant_id = ?} — scoping all queries in that
 *       transaction to the correct tenant via PostgreSQL Row Level Security.</li>
 *   <li>{@link TenantContextFilter} calls {@link #clear()} in a {@code finally} block
 *       at the end of each request to prevent ThreadLocal leakage across pooled threads.</li>
 * </ol>
 *
 * <p><strong>Why ThreadLocal?</strong> Servlet containers reuse threads across requests.
 * Without clearing, a tenant ID set for request N could bleed into request N+1 on the
 * same thread. The {@code finally} block in {@link TenantContextFilter} is the guard.
 *
 * <p>This class is intentionally not a Spring bean — it is a static utility accessed
 * directly by the DataSource wrapper and the filter.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
        // Static utility — no instantiation
    }

    /**
     * Sets the tenant ID for the current thread. Called by {@link TenantContextFilter}.
     *
     * @param tenantId the tenant UUID string from the {@code X-Tenant-Id} header
     */
    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    /**
     * Returns the tenant ID for the current thread.
     * Returns {@code null} if not set (e.g., during Flyway migrations or health checks).
     */
    public static String getCurrentTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * Clears the tenant ID for the current thread.
     * MUST be called in a {@code finally} block at the end of every request.
     * Failure to call this will cause tenant ID leakage across requests on pooled threads.
     */
    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}
