package org.task.taskmaganer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Audit loglama sırasında entity ID'si olarak kullanılacak parametreyi işaretler.
 * @AuditLog annotation'ı ile birlikte kullanılır.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityId {
    // Entity ID'si olarak kullanılacak parametreyi belirtir
}