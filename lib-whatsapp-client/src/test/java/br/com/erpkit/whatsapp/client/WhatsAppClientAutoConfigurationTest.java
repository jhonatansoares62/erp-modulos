package br.com.erpkit.whatsapp.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WhatsAppClientAutoConfiguration.class));

    @Test
    void registraBeansQuandoHabilitado() {
        runner.withPropertyValues(
                "app.modulos.whatsapp.enabled=true",
                "app.modulos.whatsapp.url=http://localhost:9193"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(WhatsAppClient.class);
            assertThat(ctx.getBean(WhatsAppClient.class)).isInstanceOf(WhatsAppClientImpl.class);
            assertThat(ctx).hasSingleBean(WhatsAppCommandRegistry.class);
        });
    }

    @Test
    void naoRegistraBeansQuandoDesabilitado() {
        runner.withPropertyValues("app.modulos.whatsapp.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(WhatsAppClient.class);
                    assertThat(ctx).doesNotHaveBean(WhatsAppCommandRegistry.class);
                });
    }

    @Test
    void naoRegistraBeansQuandoPropriedadeAusente() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(WhatsAppClient.class);
            assertThat(ctx).doesNotHaveBean(WhatsAppCommandRegistry.class);
        });
    }
}
