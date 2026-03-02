package org.task.taskmaganer.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.task.taskmaganer.annotation.AuditLog;
import org.task.taskmaganer.service.AuditLogService;

import java.lang.reflect.Method;

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogService auditLogService;
    private final AuditMetadataExtractor metadataExtractor;
    private final AuditMessageBuilder messageBuilder;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditLogAspect(AuditLogService auditLogService,
                         AuditMetadataExtractor metadataExtractor,
                         AuditMessageBuilder messageBuilder,
                         ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.metadataExtractor = metadataExtractor;
        this.messageBuilder = messageBuilder;
        this.objectMapper = objectMapper;
    }

    @Pointcut("@annotation(org.task.taskmaganer.annotation.AuditLog)")
    public void auditLogPointcut() {
    }

    @Around("auditLogPointcut()")
    public Object aroundAuditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        AuditContext context = createAuditContext(joinPoint);
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            context.markSuccess(result, System.currentTimeMillis() - startTime);
            logAudit(context);
            return result;
        } catch (Exception ex) {
            context.markFailure(ex, System.currentTimeMillis() - startTime);
            logAudit(context);
            throw ex;
        }
    }

    private AuditContext createAuditContext(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog auditLog = method.getAnnotation(AuditLog.class);

        return AuditContext.builder()
                .auditLog(auditLog)
                .action(metadataExtractor.extractAction(auditLog, method))
                .entityType(metadataExtractor.extractEntityType(auditLog, joinPoint))
                .entityId(metadataExtractor.extractEntityId(joinPoint, signature))
                .userId(metadataExtractor.extractCurrentUserId())
                .methodParams(metadataExtractor.extractMethodParameters(joinPoint))
                .build();
    }

    private void logAudit(AuditContext context) {
        if (!context.shouldLog()) {
            return;
        }

        try {
            String message = messageBuilder.build(context);
            String action = context.isSuccess()
                    ? context.getAction()
                    : context.getAction() + "_ERROR";

            auditLogService.logBusinessEvent(
                    action,
                    context.getEntityType(),
                    context.getEntityId(),
                    context.getUserId(),
                    message
            );

            logger.debug("Audit log created for action: {}", action);
        } catch (Exception ex) {
            logger.error("Failed to create audit log", ex);
        }
    }
}
