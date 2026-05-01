package br.com.erpkit.consultas.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultasClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConsultasClientAutoConfiguration.class));

    @Test
    void registraBeanQuandoHabilitado() {
        runner.withPropertyValues(
                "app.modulos.consultas.enabled=true",
                "app.modulos.consultas.url=http://localhost:9192"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(ConsultasClient.class);
            assertThat(ctx.getBean(ConsultasClient.class)).isInstanceOf(ConsultasClientImpl.class);
        });
    }

    @Test
    void naoRegistraBeanQuandoDesabilitado() {
        runner.withPropertyValues("app.modulos.consultas.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ConsultasClient.class));
    }

    @Test
    void naoRegistraBeanQuandoPropriedadeAusente() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(ConsultasClient.class));
    }
}
