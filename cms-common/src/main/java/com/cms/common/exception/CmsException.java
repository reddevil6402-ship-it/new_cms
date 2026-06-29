package com.cms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for the CMS platform.
 *
 * <p>Every service-specific exception should extend this class.
 * Carry an {@link ErrorCode} — never throw a plain {@code RuntimeException}
 * or {@code IllegalArgumentException} from business logic; always use a
 * typed {@code CmsException} so the {@code GlobalExceptionHandler} can
 * map it to the correct HTTP status and error code automatically.
 *
 * <p>Usage examples:
 * <pre>{@code
 * // Simplest form — uses the ErrorCode's default message
 * throw new CmsException(ErrorCode.CONTENT_TYPE_NOT_FOUND);
 *
 * // With a context-specific message
 * throw new CmsException(ErrorCode.CONTENT_TYPE_NOT_FOUND,
 *     "Content type '" + slug + "' does not exist in tenant " + tenantId);
 *
 * // Service-specific subclass
 * public class ContentTypeNotFoundException extends CmsException {
 *     public ContentTypeNotFoundException(String slug) {
 *         super(ErrorCode.CONTENT_TYPE_NOT_FOUND,
 *               "Content type '" + slug + "' not found");
 *     }
 * }
 * }</pre>
 */
public class CmsException extends RuntimeException {

    private final ErrorCode errorCode;

    public CmsException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public CmsException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CmsException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
