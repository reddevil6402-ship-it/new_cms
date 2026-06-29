package com.cms.common.constants;

/**
 * System-wide role name constants.
 *
 * <p>These match the role values stored in {@code iamdb.iam.roles.name}
 * and embedded in JWT {@code roles[]} claims. Use these constants in
 * {@code @PreAuthorize} expressions and role-check logic to avoid
 * raw string literals scattered across services.
 *
 * <p>Example usage:
 * <pre>{@code
 *   @PreAuthorize("hasRole('" + CmsRoles.SUPER_ADMIN + "')")
 * }</pre>
 */
public final class CmsRoles {

    /** Full platform access. Created via Flyway seed only — never via API. */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Full access within a single tenant. Manages users and settings for that tenant. */
    public static final String TENANT_ADMIN = "TENANT_ADMIN";

    /** Creates and edits content items. Cannot publish. */
    public static final String EDITOR = "EDITOR";

    /** Reviews and approves/rejects content submitted for review. */
    public static final String REVIEWER = "REVIEWER";

    /** Read-only access to published content within a tenant. */
    public static final String VIEWER = "VIEWER";

    private CmsRoles() {
        // Utility class — no instantiation
    }
}
