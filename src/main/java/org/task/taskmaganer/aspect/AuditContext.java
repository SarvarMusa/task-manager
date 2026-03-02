package org.task.taskmaganer.aspect;

import org.task.taskmaganer.annotation.AuditLog;

public class AuditContext {
    private final AuditLog auditLog;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final String userId;
    private final java.util.Map<String, Object> methodParams;
    private boolean success;
    private Object result;
    private long executionTime;
    private String errorMessage;

    private AuditContext(Builder builder) {
        this.auditLog = builder.auditLog;
        this.action = builder.action;
        this.entityType = builder.entityType;
        this.entityId = builder.entityId;
        this.userId = builder.userId;
        this.methodParams = builder.methodParams;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void markSuccess(Object result, long executionTime) {
        this.success = true;
        this.result = result;
        this.executionTime = executionTime;
    }

    public void markFailure(Exception ex, long executionTime) {
        this.success = false;
        this.errorMessage = ex.getMessage();
        this.executionTime = executionTime;
    }

    public boolean shouldLog() {
        return switch (auditLog.level()) {
            case SUCCESS -> success;
            case ERROR -> !success;
            case ALL -> true;
        };
    }

    // Getters
    public AuditLog getAuditLog() { return auditLog; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getUserId() { return userId; }
    public java.util.Map<String, Object> getMethodParams() { return methodParams; }
    public boolean isSuccess() { return success; }
    public Object getResult() { return result; }
    public long getExecutionTime() { return executionTime; }
    public String getErrorMessage() { return errorMessage; }

    public static class Builder {
        private AuditLog auditLog;
        private String action;
        private String entityType;
        private String entityId;
        private String userId;
        private java.util.Map<String, Object> methodParams;

        public Builder auditLog(AuditLog auditLog) {
            this.auditLog = auditLog;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder methodParams(java.util.Map<String, Object> methodParams) {
            this.methodParams = methodParams;
            return this;
        }

        public AuditContext build() {
            return new AuditContext(this);
        }
    }
}
