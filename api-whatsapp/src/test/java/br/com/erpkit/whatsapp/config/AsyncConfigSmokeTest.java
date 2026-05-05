package br.com.erpkit.whatsapp.config;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test do bean {@code whatsappTaskExecutor} configurado em {@link AsyncConfig}.
 *
 * <p>Wave 1 da Phase 3 — bean precisa carregar com parametros corretos antes de
 * Wave 5 introduzir {@code MensagemAsyncListener} via {@code @Async("whatsappTaskExecutor")}.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class AsyncConfigSmokeTest {

    @Autowired
    @Qualifier("whatsappTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    @Test
    @DisplayName("Bean whatsappTaskExecutor parametrizado com corePool=2 maxPool=10 queue=100")
    void bean_thread_pool_configurado() {
        assertThat(executor).isNotNull();
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(10);
        assertThat(executor.getQueueCapacity()).isEqualTo(100);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("whatsapp-async-");
    }
}
