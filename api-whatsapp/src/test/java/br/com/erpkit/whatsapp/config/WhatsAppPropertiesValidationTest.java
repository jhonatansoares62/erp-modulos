package br.com.erpkit.whatsapp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comportamento de {@link WhatsAppProperties} apos a config Meta virar runtime
 * (WHATS-CONFIG): o boot NAO falha mais quando faltam credenciais — o modulo sobe
 * "nao configurado" e o operador grava depois via {@code PUT /api/whatsapp/config}.
 *
 * <p>Antes desta mudanca havia 5 testes de fail-fast (um por credencial ausente)
 * validando {@code @NotBlank}. Eles foram REMOVIDOS junto com a validacao — a
 * ausencia de credencial agora e estado valido (isMetaConfigurado() == false), nao
 * erro de boot. A cobertura do carregamento/persistencia fica em MetaConfigServiceTest.
 *
 * <p>Mensagens/mascara continuam em "nao" sem til por consistencia com o restante.
 */
class WhatsAppPropertiesValidationTest {

    @Configuration
    @EnableConfigurationProperties(WhatsAppProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Boot SEM nenhuma credencial Meta nao falha — modulo sobe 'nao configurado'")
    void boot_sem_credenciais_nao_falha() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            WhatsAppProperties props = context.getBean(WhatsAppProperties.class);
            assertThat(props.isMetaConfigurado()).isFalse();
        });
    }

    @Test
    @DisplayName("isMetaConfigurado() true somente com as 4 credenciais preenchidas")
    void isMetaConfigurado_true_com_as_4() {
        runner.withPropertyValues(
                "app.modulos.whatsapp.phoneNumberId=x",
                "app.modulos.whatsapp.accessToken=x",
                "app.modulos.whatsapp.appSecret=x",
                "app.modulos.whatsapp.verifyToken=x"
        ).run(context -> assertThat(context.getBean(WhatsAppProperties.class).isMetaConfigurado()).isTrue());
    }

    @Test
    @DisplayName("isMetaConfigurado() false quando falta qualquer uma das credenciais")
    void isMetaConfigurado_false_faltando_uma() {
        runner.withPropertyValues(
                "app.modulos.whatsapp.phoneNumberId=x",
                "app.modulos.whatsapp.accessToken=x",
                "app.modulos.whatsapp.appSecret=x"
                // verifyToken ausente
        ).run(context -> assertThat(context.getBean(WhatsAppProperties.class).isMetaConfigurado()).isFalse());
    }

    // ---------------------------------------------------------------------
    // toString() mascara secrets (CFG-03) — inalterado
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("toString() retorna [REDACTED] para accessToken/appSecret/verifyToken")
    void toString_mascara_secrets() {
        WhatsAppProperties p = new WhatsAppProperties();
        p.setPhoneNumberId("phone-real");
        p.setAccessToken("access-real-xyz");
        p.setAppSecret("secret-real-xyz");
        p.setVerifyToken("verify-real-xyz");
        p.setErpCallbackUrl("http://erp/callback");

        String s = p.toString();

        assertThat(s)
                .doesNotContain("access-real-xyz")
                .doesNotContain("secret-real-xyz")
                .doesNotContain("verify-real-xyz");

        long redactedCount = s.split("\\[REDACTED]", -1).length - 1;
        assertThat(redactedCount).as("Esperado [REDACTED] 3x em toString()").isEqualTo(3);

        assertThat(s).contains("phone-real").contains("http://erp/callback");
    }
}
