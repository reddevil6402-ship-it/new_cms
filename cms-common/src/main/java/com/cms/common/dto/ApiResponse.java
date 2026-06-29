package com.cms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Standard API response envelope for all CMS services.
 *
 * <p>Every endpoint — success or failure — returns this structure.
 * Clients should check {@code success} first before reading {@code data} or {@code error}.
 *
 * <p>Example success response:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { ... },
 *   "error": null,
 *   "meta": { "page": 1, "size": 20, "total": 150 }
 * }
 * }</pre>
 *
 * <p>Example error response:
 * <pre>{@code
 * {
 *   "success": false,
 *   "data": null,
 *   "error": { "code": "AUTH_001", "message": "Invalid credentials" }
 * }
 * }</pre>
 *
 * @param <T> the type of the data payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;

    /** Present on success. Null on error. */
    private T data;

    /** Present on error. Null on success. */
    private ErrorResponse error;

    /**
     * Optional metadata — used for pagination info, request trace IDs, etc.
     * Keys and values are caller-defined.
     */
    private Map<String, Object> meta;

    // ── Factory methods ────────────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(meta)
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .build();
    }
}
