package org.task.taskmaganer.exception;

import org.springframework.http.HttpStatus;

/**
 * Uygulama genelinde kullanılan standart hata kodları.
 * <p>
 * Her hata kodu:
 * - Programatik olarak hataları ayırt etmeyi sağlar
 * - HTTP status kodunu belirtir
 * - İstemci için makine okunabilir bir referans sağlar
 */
public enum ErrorCode {

    // 4xx Client Errors
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "The requested resource could not be found"),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", HttpStatus.CONFLICT, "A user with this identifier already exists"),
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "The request contains invalid or missing fields"),
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, "The request could not be processed"),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "You do not have permission to access this resource"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this endpoint"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The content type is not supported"),
    CONSTRAINT_VIOLATION("CONSTRAINT_VIOLATION", HttpStatus.BAD_REQUEST, "A constraint violation occurred"),

    // 5xx Server Errors
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    DATABASE_ERROR("DATABASE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public int getStatusCode() {
        return httpStatus.value();
    }
}
