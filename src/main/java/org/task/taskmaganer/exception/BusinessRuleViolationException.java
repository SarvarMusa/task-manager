package org.task.taskmaganer.exception;

import java.util.UUID;

/**
 * İş kurallarının ihlali durumunda kullanılan exception.
 * HTTP 422 Unprocessable Entity döner.
 */
public class BusinessRuleViolationException extends RuntimeException {
    
    private final String ruleCode;
    private final UUID entityId;
    
    public BusinessRuleViolationException(String message) {
        super(message);
        this.ruleCode = null;
        this.entityId = null;
    }
    
    public BusinessRuleViolationException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
        this.entityId = null;
    }
    
    public BusinessRuleViolationException(String ruleCode, UUID entityId, String message) {
        super(message);
        this.ruleCode = ruleCode;
        this.entityId = entityId;
    }
    
    public String getRuleCode() {
        return ruleCode;
    }
    
    public UUID getEntityId() {
        return entityId;
    }
}
