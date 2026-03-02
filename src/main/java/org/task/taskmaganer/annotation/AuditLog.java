package org.task.taskmaganer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Audit loglamak istenen metodları işaretlemek için kullanılır.
 * Bu annotation ile işaretlenen metodlar otomatik olarak audit loglanır.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    
    /**
     * Audit logda görünecek action ismi (örn: "CREATE_TASK", "UPDATE_USER")
     * Varsayılan olarak metod adı kullanılır
     */
    String action() default "";
    
    /**
     * Entity tipi (örn: "TASK", "USER", "PROJECT")
     * Varsayılan olarak sınıf adı kullanılır
     */
    String entityType() default "";
    
    /**
     * Audit log mesajı - daha detaylı açıklama
     * Varsayılan olarak boş, otomatik mesaj oluşturulur
     */
    String message() default "";
    
    /**
     * Parametrelerden entity ID'sini almak için parametre indeksi
     * -1 ise parametreler arasında @EntityId annotation'ı aranır
     */
    int entityIdIndex() default -1;
    
    /**
     * Audit log seviyesi - başarılı mı, hatalı mı, ikisi de mi loglansın
     */
    AuditLevel level() default AuditLevel.ALL;
    
    enum AuditLevel {
        SUCCESS,  // Sadece başarılı durumlar
        ERROR,    // Sadece hatalı durumlar  
        ALL       // Hem başarılı hem hatalı durumlar
    }
}