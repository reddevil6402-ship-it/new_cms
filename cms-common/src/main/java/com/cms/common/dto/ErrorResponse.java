package com.cms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Error payload embedded inside {@link ApiResponse} when {@code success = false}.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "code":    "VAL_001",
 *   "message": "Validation failed",
 *   "details": [
 *     "email: must not be blank",
 *     "password: must be at least 8 characters"
 *   ]
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Machine-readable error code. Matches a value from {@link com.cms.common.exception.ErrorCode}. */
    private String code;

    /** Human-readable error description safe to display in a UI. */
    private String message;

    /**
     * Optional field-level or item-level detail messages.
     * Used for validation errors to list each failing field.
     */
    private List<String> details;
}
