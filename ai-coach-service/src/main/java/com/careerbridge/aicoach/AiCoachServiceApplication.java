package com.careerbridge.aicoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @EnableAsync is required here even though Boot 4 auto-configures the executor bean
 * (applicationTaskExecutor) -- TaskExecutionAutoConfiguration only builds the executor, it never
 * registers the AsyncAnnotationBeanPostProcessor that makes @Async actually intercept calls.
 * See CatalogRefresher for why no Executor/TaskExecutor @Bean is declared anywhere in this service.
 */
@EnableAsync
@SpringBootApplication
public class AiCoachServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCoachServiceApplication.class, args);
    }
}
