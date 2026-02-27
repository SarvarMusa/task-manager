package org.task.taskmaganer.exception;

/**
 * Geçersiz istek parametreleri veya formatı için kullanılan exception.
 * HTTP 400 Bad Request döner.
 */
public class InvalidRequestException extends RuntimeException {
    
    private final String field;
    
    public InvalidRequestException(String message) {
        super(message);
        this.field = null;
    }
    
    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }
    
    public String getField() {
        return field;
    }
}
