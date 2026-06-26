package com.project.back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 실행기 설정
 *
 * <p>{@code @EnableAsync}만 두면 {@code @Async}는 기본 {@code SimpleAsyncTaskExecutor}를 사용해
 * 작업마다 새 스레드를 생성한다. 이 설정은 bounded {@code ThreadPoolTaskExecutor}를 등록하여
 * 스레드 수를 제한하고 재사용한다.</p>
 *
 * <ul>
 *   <li>corePoolSize(2) — 평상시 유지 스레드 수</li>
 *   <li>maxPoolSize(10) — 최대 스레드 수</li>
 *   <li>queueCapacity(50) — core 포화 시 대기열 크기</li>
 *   <li>대기열까지 포화되면 호출자 스레드에서 실행(CallerRunsPolicy)하여 요청을 누락하지 않는다.</li>
 * </ul>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }
}
