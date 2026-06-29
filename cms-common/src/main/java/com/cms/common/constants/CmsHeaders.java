package com.cms.common.constants;

/**
 * HTTP header name constants used across all CMS services.
 *
 * <p>The API Gateway extracts these from the JWT and forwards them to
 * downstream services on every request. Services read them from the
 * incoming request headers — never re-parse the JWT themselves.
 */
public final class CmsHeaders {

    /** Tenant UUID forwarded by the API Gateway from the JWT tenantId claim. */
    public static final String X_TENANT_ID = "X-Tenant-Id";

    /** User UUID forwarded by the API Gateway from the JWT userId claim. */
    public static final String X_USER_ID = "X-User-Id";

    /** Tenant code (slug) forwarded by the API Gateway from the JWT tenantCode claim. */
    public static final String X_TENANT_CODE = "X-Tenant-Code";

    private CmsHeaders() {
        // Utility class — no instantiation
    }
}
