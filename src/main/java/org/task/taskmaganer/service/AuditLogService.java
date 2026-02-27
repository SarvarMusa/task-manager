package org.task.taskmaganer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Yapılandırılmış (structured) logging için utility service.
 * <p>
 * İş event'lerini ve önemli business action'ları loglamak için kullanılır.
 * 
 * Kullanım senaryoları:
 * - Task oluşturuldu/silindi/güncellendi
 * - User login/logout
 * - Business rule violations
 * - Audit trail
 */
@Service
public class AuditLogService {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String ACTION_LOGIN = "LOGIN";
    private static final String ACTION_LOGOUT = "LOGOUT";
    private static final String ACTION_VIEW = "VIEW";

    /**
     * Task oluşturulduğunda loglar.
     */
    public void logTaskCreated(String taskId, String taskTitle, String createdBy, String assigneeId) {
        MDC.put("action", ACTION_CREATE);
        MDC.put("entityType", "TASK");
        MDC.put("entityId", taskId);
        MDC.put("userId", createdBy);
        
        auditLog.info("Task created | id={} | title={} | assignee={} | by={}", 
                taskId, taskTitle, assigneeId, createdBy);
        
        clearMdc();
    }

    /**
     * Task güncellendiğinde loglar.
     */
    public void logTaskUpdated(String taskId, String taskTitle, String updatedBy, Map<String, Object> changes) {
        MDC.put("action", ACTION_UPDATE);
        MDC.put("entityType", "TASK");
        MDC.put("entityId", taskId);
        MDC.put("userId", updatedBy);
        
        auditLog.info("Task updated | id={} | title={} | changes={} | by={}", 
                taskId, taskTitle, changes, updatedBy);
        
        clearMdc();
    }

    /**
     * Task silindiğinde loglar (soft delete).
     */
    public void logTaskDeleted(String taskId, String taskTitle, String deletedBy) {
        MDC.put("action", ACTION_DELETE);
        MDC.put("entityType", "TASK");
        MDC.put("entityId", taskId);
        MDC.put("userId", deletedBy);
        
        auditLog.info("Task deleted | id={} | title={} | by={}", 
                taskId, taskTitle, deletedBy);
        
        clearMdc();
    }

    /**
     * User login olduğunda loglar.
     */
    public void logUserLogin(String userId, String username, String ipAddress, boolean success) {
        MDC.put("action", ACTION_LOGIN);
        MDC.put("entityType", "USER");
        MDC.put("entityId", userId);
        MDC.put("userId", userId);
        
        if (success) {
            auditLog.info("User login successful | username={} | ip={} | userId={}", 
                    username, ipAddress, userId);
        } else {
            auditLog.warn("User login failed | username={} | ip={}", 
                    username, ipAddress);
        }
        
        clearMdc();
    }

    /**
     * User logout olduğunda loglar.
     */
    public void logUserLogout(String userId, String username) {
        MDC.put("action", ACTION_LOGOUT);
        MDC.put("entityType", "USER");
        MDC.put("entityId", userId);
        MDC.put("userId", userId);
        
        auditLog.info("User logout | username={} | userId={}", 
                username, userId);
        
        clearMdc();
    }

    /**
     * Özel business event loglar.
     */
    public void logBusinessEvent(String action, String entityType, String entityId, 
                                  String userId, String description) {
        MDC.put("action", action);
        MDC.put("entityType", entityType);
        MDC.put("entityId", entityId);
        MDC.put("userId", userId);
        
        auditLog.info("Business event | action={} | entity={} | id={} | desc={} | by={}", 
                action, entityType, entityId, description, userId);
        
        clearMdc();
    }

    /**
     * Security event loglar - şüpheli aktiviteler.
     */
    public void logSecurityEvent(String event, String userId, String ipAddress, String details) {
        MDC.put("action", "SECURITY");
        MDC.put("event", event);
        
        auditLog.warn("Security event | event={} | userId={} | ip={} | details={}", 
                event, userId, ipAddress, details);
        
        clearMdc();
    }

    /**
     * Performance metrik logları.
     */
    public void logPerformanceMetric(String operation, long durationMs, String details) {
        MDC.put("metricType", "PERFORMANCE");
        MDC.put("operation", operation);
        
        if (durationMs > 1000) {
            log.warn("Slow operation detected | operation={} | duration={}ms | details={}", 
                    operation, durationMs, details);
        } else {
            log.debug("Performance metric | operation={} | duration={}ms", 
                    operation, durationMs);
        }
        
        clearMdc();
    }

    private void clearMdc() {
        MDC.clear();
    }
}
