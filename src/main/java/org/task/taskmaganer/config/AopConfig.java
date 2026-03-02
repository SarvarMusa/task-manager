package org.task.taskmaganer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AspectJ AOP desteğini etkinleştirir.
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    // AOP aspect'lerinin otomatik olarak algılanması için boş config sınıfı
}