package br.com.erpkit.whatsapp.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Substitui o bean {@code whatsappTaskExecutor} de producao em testes selecionados.
 *
 * <p>{@link SyncTaskExecutor} executa {@code task.run()} inline na thread chamadora —
 * listener {@code @Async} vira sincrono em test. Combinado com
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, o listener dispara apos
 * commit do save() do {@code MensagemService} — quando o metodo
 * {@code processarWebhook} retorna, todo o flow ja rodou (sync) — assertions DB
 * seguras sem Awaitility.
 *
 * <p><b>NAO usar em SC-1 timing test</b> — esse precisa de executor real
 * (pool dedicado da {@code AsyncConfig}) para provar que o ack 200 retorna em
 * &lt;1s mesmo com ERP delay 10s. Em {@link WebhookAsyncTimingIntegrationTest}
 * NAO importar esta classe.
 *
 * <p>Uso:
 * <pre>
 * &#64;SpringBootTest
 * &#64;Import(AsyncTestConfig.class)
 * class MyIntegrationTest { ... }
 * </pre>
 *
 * <p><b>{@code @Primary}:</b> garante override do bean
 * {@code whatsappTaskExecutor} de producao (definido em
 * {@code br.com.erpkit.whatsapp.config.AsyncConfig}). Spring resolve por nome
 * (qualifier do {@code @Async("whatsappTaskExecutor")}); {@code @Primary} eh
 * defesa em profundidade caso outro bean seja resolvido por tipo.
 */
@TestConfiguration
public class AsyncTestConfig {

    @Bean(name = "whatsappTaskExecutor")
    @Primary
    public TaskExecutor whatsappTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
