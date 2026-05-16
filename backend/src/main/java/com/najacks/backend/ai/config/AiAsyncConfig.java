package com.najacks.backend.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
public class AiAsyncConfig {

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(3);
        ex.setMaxPoolSize(10);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("ai-task-");
        ex.initialize();
        return ex;
    }

    @Bean("notionSyncExecutor")
    public Executor notionSyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(5);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("notion-sync-");
        ex.initialize();
        return ex;
    }

    @Bean("streamPollExecutor")
    public Executor streamPollExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(5);
        ex.setMaxPoolSize(20);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("stream-poll-");
        ex.initialize();
        return ex;
    }
}
