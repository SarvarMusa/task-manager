package org.task.taskmaganer.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Schema(description = "Hata yanıt modeli")
public class ErrorResponse {

    @Schema(description = "Hata zaman damgası", example = "2024-02-27T10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status kodu", example = "404")
    private int status;

    @Schema(description = "Hata kodu (programatik)", example = "RESOURCE_NOT_FOUND")
    private String errorCode;

    @Schema(description = "Hata mesajı", example = "Task not found with id: 550e8400-e29b-41d4-a716-446655440000")
    private String message;

    @Schema(description = "Hata detayları (validasyon hataları için)")
    private Map<String, String> errors;

    @Schema(description = "İstek yapılan path", example = "/api/v1/tasks/123")
    private String path;

    @Schema(description = "Hata açıklaması", example = "The requested resource could not be found")
    private String error;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
        this.errors = new HashMap<>();
    }

    public ErrorResponse(int status, String errorCode, String message, String path, String error) {
        this();
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
        this.error = error;
    }

    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    public static class ErrorResponseBuilder {
        private int status;
        private String errorCode;
        private String message;
        private String path;
        private String error;
        private Map<String, String> errors = new HashMap<>();

        public ErrorResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        public ErrorResponseBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public ErrorResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ErrorResponseBuilder path(String path) {
            this.path = path;
            return this;
        }

        public ErrorResponseBuilder error(String error) {
            this.error = error;
            return this;
        }

        public ErrorResponseBuilder errors(Map<String, String> errors) {
            this.errors = errors != null ? errors : new HashMap<>();
            return this;
        }

        public ErrorResponse build() {
            ErrorResponse response = new ErrorResponse();
            response.status = this.status;
            response.errorCode = this.errorCode;
            response.message = this.message;
            response.path = this.path;
            response.error = this.error;
            response.errors = this.errors;
            return response;
        }
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors != null ? errors : new HashMap<>();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
