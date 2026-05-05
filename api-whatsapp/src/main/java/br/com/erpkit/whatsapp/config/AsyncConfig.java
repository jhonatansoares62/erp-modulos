package br.com.erpkit.whatsapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Pool dedicado para o {@code MensagemAsyncListener} (D-02 do CONTEXT).
 *
 * <p>Pool dedicado em vez de SimpleAsyncTaskExecutor: SimpleAsync cria thread por task —
 * em pico de mensagens, OOM. Pool fixo com queueCapacity 100 + CallerRunsPolicy degrada
 * graciosamente: sob estresse extremo, listener roda inline na thread chamadora.
 *
 * <p><b>Test profile (Wave 6):</b> sobrescreve {@code whatsappTaskExecutor} com
 * SyncTaskExecutor via {@code @TestConfiguration} para tests E2E manterem assertions
 * DB sincronas sem Awaitility.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "whatsappTaskExecutor")
    public ThreadPoolTaskExecutor whatsappTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("whatsapp-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
