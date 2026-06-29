package com.cms.common.exception;

import com.cms.common.dto.ApiResponse;
import com.cms.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Global exception handler shared across all CMS services via cms-common auto-configuration.
 *
 * <p>Maps {@link CmsException} subtypes, Spring validation errors, and unexpected
 * exceptions to the standard {@link ApiResponse} envelope with appropriate HTTP status codes.
 *
 * <p>Services can override specific handlers by declaring their own
 * {@code @ExceptionHandler} methods — Spring picks the most specific handler first.
 *
 * <p><strong>Security note:</strong> The generic {@code Exception} handler logs the
 * full stack trace internally but returns only a safe {@code SYS_001} message to the client.
 * Never expose stack traces or internal messages in the response body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles all typed CMS exceptions — the primary handler.
     * The HTTP status and error code come directly from the {@link ErrorCode} enum.
     */
    @ExceptionHandler(CmsException.class)
    public ResponseEntity<ApiResponse<Void>> handleCmsException(CmsException ex) {
        log.warn("CmsException [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(error));
    }

    /**
     * Handles @Valid / @Validated annotation failures on request bodies.
     * Returns all field-level errors in the details list.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(ErrorCode.VALIDATION_ERROR.getDefaultMessage())
                .details(details)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(error));
    }

    /**
     * Handles path variable or request parameter type mismatches
     * (e.g., a UUID field receiving a non-UUID string).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' has invalid value: %s", ex.getName(), ex.getValue());

        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(message)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(error));
    }

    /**
     * Catch-all for any unhandled exception.
     * Logs the full exception internally but returns only a safe generic message to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception — this should not reach production without investigation", ex);

        ErrorResponse error = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(ErrorCode.INTERNAL_ERROR.getDefaultMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(error));
    }
}
