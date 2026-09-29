package com.manacommunity.media.config;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * General application beans — Apache Tika for MIME detection,
 * and an async thread pool for thumbnail generation.
 */
@Configuration
public class AppConfig {

    /**
     * Apache Tika MIME-type detector.
     * Thread-safe singleton — reuse one instance throughout the application.
     */
    @Bean
    public Tika tika() {
        return new Tika();
    }

    /**
     * Dedicated async executor for image-processing tasks
     * (thumbnail generation, resize variants).
     */
    @Bean(name = "mediaTaskExecutor")
    public Executor mediaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("media-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
