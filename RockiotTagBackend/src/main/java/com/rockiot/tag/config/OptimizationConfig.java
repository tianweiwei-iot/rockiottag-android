package com.rockiot.tag.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 第二阶段优化配置类
 * 1. 启用缓存
 * 2. 启用异步处理
 * 3. 配置线程池
 */
@Configuration
@EnableCaching  // 启用缓存
@EnableAsync    // 启用异步处理
public class OptimizationConfig {
    
    /**
     * 缓存管理器配置
     * 使用简单的内存缓存（ConcurrentMapCacheManager）
     * 如果后续需要 Redis，可改为 RedisCacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "userDevices",        // 用户设备列表缓存
            "deviceLocation",     // 设备位置缓存
            "deviceHistory"       // 设备历史缓存
        );
    }
    
    /**
     * 异步处理线程池配置
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);      // 核心线程数
        executor.setMaxPoolSize(50);       // 最大线程数
        executor.setQueueCapacity(1000);   // 队列容量
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
