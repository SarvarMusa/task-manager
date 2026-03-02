package org.task.taskmaganer.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.task.taskmaganer.annotation.AuditLog;
import org.task.taskmaganer.annotation.EntityId;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditMetadataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AuditMetadataExtractor.class);

    private static final String HTTP_HEADER_USER_ID = "X-User-Id";
    private static final String DEFAULT_USER_ID = "system";

    public String extractAction(AuditLog auditLog, Method method) {
        if (!auditLog.action().isEmpty()) {
            return auditLog.action();
        }
        return inferActionFromMethodName(method.getName());
    }

    public String extractEntityType(AuditLog auditLog, JoinPoint joinPoint) {
        if (!auditLog.entityType().isEmpty()) {
            return auditLog.entityType();
        }
        return inferEntityTypeFromClassName(joinPoint.getTarget().getClass().getSimpleName());
    }

    public String extractEntityId(JoinPoint joinPoint, MethodSignature signature) {
        Object[] args = joinPoint.getArgs();
        Annotation[][] annotations = signature.getMethod().getParameterAnnotations();

        // Find @EntityId annotation
        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation instanceof EntityId) {
                    return formatArg(args[i]);
                }
            }
        }

        // Fallback: first String/UUID parameter
        for (Object arg : args) {
            if (arg instanceof String || arg instanceof UUID) {
                return formatArg(arg);
            }
        }

        return null;
    }

    public String extractCurrentUserId() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userId = request.getHeader(HTTP_HEADER_USER_ID);
                if (userId != null) {
                    return userId;
                }
            }
        } catch (Exception ex) {
            logger.debug("Could not extract user ID", ex);
        }

        return DEFAULT_USER_ID;
    }

    public Map<String, Object> extractMethodParameters(JoinPoint joinPoint) {
        Map<String, Object> params = new LinkedHashMap<>();

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] names = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < names.length; i++) {
                params.put(names[i], simplifyArg(args[i]));
            }
        } catch (Exception ex) {
            logger.debug("Could not extract parameters", ex);
            params.put("error", ex.getMessage());
        }

        return params;
    }

    private String inferActionFromMethodName(String methodName) {
        String name = methodName.toLowerCase();
        if (name.startsWith("create")) return "CREATE";
        if (name.startsWith("update") || name.startsWith("edit")) return "UPDATE";
        if (name.startsWith("delete") || name.startsWith("remove")) return "DELETE";
        if (name.startsWith("get") || name.startsWith("find") || name.startsWith("search")) return "READ";
        return methodName.toUpperCase();
    }

    private String inferEntityTypeFromClassName(String className) {
        String name = className;
        if (name.endsWith("Service")) name = name.replace("Service", "");
        else if (name.endsWith("Controller")) name = name.replace("Controller", "");
        return name.toUpperCase();
    }

    private String formatArg(Object arg) {
        return arg != null ? arg.toString() : null;
    }

    private Object simplifyArg(Object arg) {
        if (arg == null) return null;

        String className = arg.getClass().getName();
        boolean isCustomType = className.startsWith("org.task");
        boolean isCollection = className.startsWith("java.util");

        if (isCustomType || isCollection) {
            return arg.getClass().getSimpleName();
        }

        return arg;
    }
}
