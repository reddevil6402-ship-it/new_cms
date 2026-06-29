package com.cms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Enum of all system-level error codes across every CMS service.
 *
 * <p>Each code carries a machine-readable string code (safe to expose to clients),
 * a default human-readable message, and the HTTP status to return.
 *
 * <p>Code prefix convention:
 * <ul>
 *   <li>{@code AUTH_xxx} — Authentication errors (who are you?)</li>
 *   <li>{@code AUTHZ_xxx} — Authorization errors (what can you do?)</li>
 *   <li>{@code RES_xxx}  — Resource-level errors (not found, conflict)</li>
 *   <li>{@code VAL_xxx}  — Validation errors (bad input)</li>
 *   <li>{@code SCH_xxx}  — Schema/content-type errors</li>
 *   <li>{@code WF_xxx}   — Workflow errors</li>
 *   <li>{@code SYS_xxx}  — System/internal errors</li>
 * </ul>
 */
public enum ErrorCode {

    // ── Authentication ────────────────────────────────────────────────────────
    /** Generic credential failure. Intentionally vague to prevent user enumeration. */
    INVALID_CREDENTIALS("AUTH_001", "Invalid email or password", HttpStatus.UNAUTHORIZED),

    TENANT_NOT_FOUND("AUTH_002", "Tenant not found", HttpStatus.UNAUTHORIZED),

    TENANT_INACTIVE("AUTH_003", "Tenant account is inactive or suspended", HttpStatus.FORBIDDEN),

    ACCOUNT_LOCKED("AUTH_004", "Account is temporarily locked due to too many failed attempts", HttpStatus.LOCKED),

    ACCOUNT_INACTIVE("AUTH_005", "Account is inactive or pending activation", HttpStatus.FORBIDDEN),

    INVALID_TOKEN("AUTH_006", "Invalid or malformed authentication token", HttpStatus.UNAUTHORIZED),

    TOKEN_EXPIRED("AUTH_007", "Authentication token has expired", HttpStatus.UNAUTHORIZED),

    REFRESH_TOKEN_INVALID("AUTH_008", "Refresh token is invalid or has been revoked", HttpStatus.UNAUTHORIZED),

    // ── Authorization ─────────────────────────────────────────────────────────
    ACCESS_DENIED("AUTHZ_001", "You do not have permission to perform this action", HttpStatus.FORBIDDEN),

    // ── Resource ──────────────────────────────────────────────────────────────
    RESOURCE_NOT_FOUND("RES_001", "The requested resource was not found", HttpStatus.NOT_FOUND),

    DUPLICATE_RESOURCE("RES_002", "A resource with this identifier already exists", HttpStatus.CONFLICT),

    // ── Validation ────────────────────────────────────────────────────────────
    VALIDATION_ERROR("VAL_001", "Request validation failed", HttpStatus.BAD_REQUEST),

    INVALID_FIELD_TYPE("VAL_002", "Invalid field type specified", HttpStatus.BAD_REQUEST),

    MISSING_REQUIRED_FIELD("VAL_003", "One or more required fields are missing", HttpStatus.BAD_REQUEST),

    // ── Schema service ────────────────────────────────────────────────────────
    CONTENT_TYPE_NOT_FOUND("SCH_001", "Content type not found", HttpStatus.NOT_FOUND),

    CONTENT_TYPE_SLUG_TAKEN("SCH_002", "A content type with this slug already exists in this tenant", HttpStatus.CONFLICT),

    FIELD_DEFINITION_NOT_FOUND("SCH_003", "Field definition not found", HttpStatus.NOT_FOUND),

    // ── Workflow service ──────────────────────────────────────────────────────
    WORKFLOW_NOT_FOUND("WF_001", "Workflow definition not found", HttpStatus.NOT_FOUND),

    INVALID_WORKFLOW_TRANSITION("WF_002", "This workflow transition is not permitted from the current state", HttpStatus.UNPROCESSABLE_ENTITY),

    WORKFLOW_ALREADY_ASSIGNED("WF_003", "A workflow is already assigned to this content item", HttpStatus.CONFLICT),

    // ── System ────────────────────────────────────────────────────────────────
    INTERNAL_ERROR("SYS_001", "An unexpected internal error occurred", HttpStatus.INTERNAL_SERVER_ERROR),

    SERVICE_UNAVAILABLE("SYS_002", "A required downstream service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
