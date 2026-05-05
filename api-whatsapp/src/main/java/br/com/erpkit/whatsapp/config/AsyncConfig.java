package br.com.erpkit.whatsapp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
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
 * SyncTaskExecutor via {@code @TestConfiguration AsyncTestConfig} (em src/test/java).
 * O bean de prod e anotado com {@link ConditionalOnMissingBean}(name = "whatsappTaskExecutor")
 * para que o test bean (registrado primeiro via {@code @Import(AsyncTestConfig.class)})
 * tenha precedencia sem precisar de override flag (Wave 6 PLAN 03-06).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "whatsappTaskExecutor")
    @ConditionalOnMissingBean(name = "whatsappTaskExecutor")
    public TaskExecutor whatsappTaskExecutor() {
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
