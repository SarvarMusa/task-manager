package org.task.taskmaganer.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.task.taskmaganer.dto.response.ErrorResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Global exception handler for the application.
 * <p>
 * Clean Code principles applied:
 * - SRP: Each handler deals with one exception type
 * - DRY: Common error response building logic extracted
 * - Builder Pattern: ErrorResponse construction
 * - Type Safety: ErrorCode enum instead of strings
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_LOG_TEMPLATE = "Error occurred - Code: {}, Path: {}, Message: {}";

    // ==================== 4xx CLIENT ERRORS ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn(ERROR_LOG_TEMPLATE, ErrorCode.RESOURCE_NOT_FOUND.getCode(), request.getRequestURI(), ex.getMessage());

        ErrorResponse response = buildErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(
            UserAlreadyExistsException ex, HttpServletRequest request) {
        log.warn(ERROR_LOG_TEMPLATE, ErrorCode.USER_ALREADY_EXISTS.getCode(), request.getRequestURI(), ex.getMessage());

        ErrorResponse response = buildErrorResponse(
                ErrorCode.USER_ALREADY_EXISTS,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(
            InvalidRequestException ex, HttpServletRequest request) {
        log.warn(ERROR_LOG_TEMPLATE, ErrorCode.BAD_REQUEST.getCode(), request.getRequestURI(), ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        if (ex.getField() != null) {
            errors.put(ex.getField(), ex.getMessage());
        }

        ErrorResponse response = ErrorResponse.builder()
                .status(ErrorCode.BAD_REQUEST.getStatusCode())
                .errorCode(ErrorCode.BAD_REQUEST.getCode())
                .message("Invalid request: " + ex.getMessage())
                .path(request.getRequestURI())
                .error(ErrorCode.BAD_REQUEST.getDefaultMessage())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn(ERROR_LOG_TEMPLATE, ErrorCode.FORBIDDEN.getCode(), request.getRequestURI(), ex.getMessage());

        ErrorResponse response = buildErrorResponse(
                ErrorCode.FORBIDDEN,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolationException(
            BusinessRuleViolationException ex, HttpServletRequest request) {
        log.warn(ERROR_LOG_TEMPLATE, ErrorCode.BAD_REQUEST.getCode(), request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .errorCode(ex.getRuleCode() != null ? ex.getRuleCode() : "BUSINESS_RULE_VIOLATION")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .error("Business rule violation")
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    // ==================== VALIDATION ERRORS ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed - Path: {}, Errors: {}", request.getRequestURI(), ex.getErrorCount());

        Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
                .filter(error -> error instanceof FieldError)
                .map(error -> (FieldError) error)
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"),
                        (existing, replacement) -> existing + ", " + replacement
                ));

        ErrorResponse response = ErrorResponse.builder()
                .status(ErrorCode.VALIDATION_ERROR.getStatusCode())
                .errorCode(ErrorCode.VALIDATION_ERROR.getCode())
                .message("Validation failed. Please check your input.")
                .path(request.getRequestURI())
                .error(ErrorCode.VALIDATION_ERROR.getDefaultMessage())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation - Path: {}", request.getRequestURI());

        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing + ", " + replacement
                ));

        ErrorResponse response = ErrorResponse.builder()
                .status(ErrorCode.CONSTRAINT_VIOLATION.getStatusCode())
                .errorCode(ErrorCode.CONSTRAINT_VIOLATION.getCode())
                .message("Constraint violation occurred.")
                .path(request.getRequestURI())
                .error(ErrorCode.CONSTRAINT_VIOLATION.getDefaultMessage())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== REQUEST ERRORS ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported - Method: {}, Path: {}", ex.getMethod(), request.getRequestURI());

        ErrorResponse response = buildErrorResponse(
                ErrorCode.METHOD_NOT_ALLOWED,
                "Method '" + ex.getMethod() + "' is not supported for this endpoint.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("No handler found - Path: {}, Method: {}", request.getRequestURI(), ex.getHttpMethod());

        ErrorResponse response = buildErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND,
                "The endpoint '" + request.getRequestURI() + "' does not exist.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported - Type: {}, Path: {}", ex.getContentType(), request.getRequestURI());

        ErrorResponse response = buildErrorResponse(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content type '" + ex.getContentType() + "' is not supported. Use 'application/json'.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Message not readable - Path: {}, Error: {}", request.getRequestURI(), ex.getMessage());

        String message = "Request body could not be read. Please check JSON format.";
        if (ex.getMessage() != null && ex.getMessage().contains("UUID")) {
            message = "Invalid UUID format. Please provide a valid UUID.";
        }

        ErrorResponse response = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter - Parameter: {}, Path: {}", ex.getParameterName(), request.getRequestURI());

        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getParameterName(), "Required parameter '" + ex.getParameterName() + "' is not present.");

        ErrorResponse response = ErrorResponse.builder()
                .status(ErrorCode.BAD_REQUEST.getStatusCode())
                .errorCode(ErrorCode.BAD_REQUEST.getCode())
                .message("Required parameter is missing.")
                .path(request.getRequestURI())
                .error(ErrorCode.BAD_REQUEST.getDefaultMessage())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch - Parameter: {}, Value: {}, Path: {}",
                ex.getName(), ex.getValue(), request.getRequestURI());

        String message = String.format("Parameter '%s' has invalid value '%s'. Expected type: %s",
                ex.getName(), ex.getValue(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getName(), message);

        ErrorResponse response = ErrorResponse.builder()
                .status(ErrorCode.BAD_REQUEST.getStatusCode())
                .errorCode(ErrorCode.BAD_REQUEST.getCode())
                .message("Parameter type mismatch.")
                .path(request.getRequestURI())
                .error(ErrorCode.BAD_REQUEST.getDefaultMessage())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== DATABASE ERRORS ====================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Database integrity violation - Path: {}, Error: {}", request.getRequestURI(), ex.getMessage());

        String message = "Database constraint violation. Please check your data.";
        if (ex.getMessage() != null && ex.getMessage().contains("unique")) {
            message = "A record with this value already exists.";
        }

        ErrorResponse response = buildErrorResponse(
                ErrorCode.DATABASE_ERROR,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ==================== 5xx SERVER ERRORS ====================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error - Path: {}, Error: {}", request.getRequestURI(), ex.getMessage(), ex);

        // Don't expose internal details in production
        String message = "An unexpected error occurred. Please try again later.";

        ErrorResponse response = buildErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ==================== HELPER METHODS ====================

    private ErrorResponse buildErrorResponse(ErrorCode errorCode, String message, String path) {
        return ErrorResponse.builder()
                .status(errorCode.getStatusCode())
                .errorCode(errorCode.getCode())
                .message(message)
                .path(path)
                .error(errorCode.getDefaultMessage())
                .build();
    }
}
