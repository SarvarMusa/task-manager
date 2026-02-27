package org.task.taskmaganer.config;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.UUID;

/**
 * Otomatik request/response ve exception logging için Aspect.
 * <p>
 * Tüm controller metodlarını otomatik loglar:
 * - Request: method, path, params, body
 * - Response: status, duration, result
 * - Exception: stack trace ile birlikte
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);
    private static final String CORRELATION_ID = "correlationId";

    /**
     * Tüm controller metodlarını hedef alır.
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restController() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public void requestMapping() {
    }

    /**
     * Controller metodlarını sarar ve request/response bilgilerini loglar.
     */
    @Around("restController() && requestMapping()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        
        // Correlation ID oluştur veya al
        String correlationId = getOrCreateCorrelationId();
        MDC.put(CORRELATION_ID, correlationId);
        
        HttpServletRequest request = getCurrentRequest();
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String path = request != null ? request.getRequestURI() : "UNKNOWN";
        String queryString = request != null ? request.getQueryString() : null;
        
        try {
            // Request logla (sadece DEV ve TEST'te body, PROD'da sadece metadata)
            if (log.isDebugEnabled()) {
                log.debug("[{}] → {} {}{}", correlationId, method, path, 
                        queryString != null ? "?" + queryString : "");
            } else {
                log.info("[{}] → {} {}", correlationId, method, path);
            }
            
            // Metodu çalıştır
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - start;
            
            // Response logla
            if (log.isDebugEnabled()) {
                log.debug("[{}] ← {} {} - {}ms - OK", correlationId, method, path, duration);
            } else {
                log.info("[{}] ← {} {} - {}ms", correlationId, method, path, duration);
            }
            
            return result;
            
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}] ✕ {} {} - {}ms - Exception: {}", 
                    correlationId, method, path, duration, ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove(CORRELATION_ID);
        }
    }

    /**
     * Exception durumlarını loglar (aspect'te yakalanmayanlar için).
     */
    @AfterThrowing(pointcut = "restController()", throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        String correlationId = MDC.get(CORRELATION_ID);
        if (correlationId == null) {
            correlationId = "NO_CORR_ID";
        }
        
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        
        log.error("[{}] Exception in {}.{}() - {}: {}", 
                correlationId, className, methodName, 
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }

    private String getOrCreateCorrelationId() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            String correlationId = request.getHeader("X-Correlation-Id");
            if (correlationId != null && !correlationId.isEmpty()) {
                return correlationId;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}
