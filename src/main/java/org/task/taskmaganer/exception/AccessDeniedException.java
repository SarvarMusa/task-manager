package org.task.taskmaganer.exception;

/**
 * Yetkisiz erişim için kullanılan exception.
 * HTTP 403 Forbidden döner.
 */
public class AccessDeniedException extends RuntimeException {
    
    public AccessDeniedException(String message) {
        super(message);
    }
}
