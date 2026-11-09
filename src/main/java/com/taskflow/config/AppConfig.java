package com.taskflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(TaskFlowProperties.class)
public class AppConfig {

    /** Reloj con la zona horaria del negocio: se usa para fechas límite y recordatorios. */
    @Bean
    public Clock clock(TaskFlowProperties props) {
        return Clock.system(ZoneId.of(props.timezone()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Ejecutor para la indexación asíncrona en Qdrant y llamadas a n8n. */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("taskflow-async-");
        executor.initialize();
        return executor;
    }
}
