package br.com.erpkit.whatsapp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation fail-fast em {@link WhatsAppProperties}.
 *
 * <p>Cobertura (per RESEARCH §12.4 — 7 cenarios). O happy path (boot com 5 propriedades
 * carrega contexto Spring inteiro) fica em {@link WhatsAppPropertiesHappyPathTest} para
 * isolar @SpringBootTest do ApplicationContextRunner usado nos 5 testes de fail-fast.
 *
 * <p>Mensagens PT-BR usam "nao" sem til — qualquer correcao automatica para "não" quebra
 * os matches de {@code .hasMessageContaining(...)}. WhatsAppProperties.java tem o mesmo padrao.
 */
class WhatsAppPropertiesValidationTest {

    /** Configuracao auxiliar registrada via {@link ApplicationContextRunner#withUserConfiguration}. */
    @Configuration
    @EnableConfigurationProperties(WhatsAppProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    // ---------------------------------------------------------------------
    // Fail-fast: cada teste omite UM campo. Ausencia (== null) dispara
    // ConstraintViolation com a mensagem PT-BR. ApplicationContextRunner
    // captura a falha sem precisar try/catch manual.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Boot sem phoneNumberId falha com 'WHATSAPP_PHONE_NUMBER_ID nao definida'")
    void boot_sem_phoneNumberId_falha() {
        runner.withPropertyValues(
                // phoneNumberId omitido deliberadamente
                "app.modulos.whatsapp.accessToken=x",
                "app.modulos.whatsapp.appSecret=x",
                "app.modulos.whatsapp.verifyToken=x",
                "app.modulos.whatsapp.erpCallbackUrl=http://localhost:0/test"
        ).run(context -> assertThat(context).hasFailed()
                .getFailure()
                // hasStackTraceContaining percorre toda a chain de causas — mensagem PT-BR
                // do @NotBlank fica na BindValidationException (root cause), nao na top-level
                // ConfigurationPropertiesBindException.
                .hasStackTraceContaining("WHATSAPP_PHONE_NUMBER_ID nao definida"));
    }

    @Test
    @DisplayName("Boot sem accessToken falha com 'WHATSAPP_ACCESS_TOKEN nao definida'")
    void boot_sem_accessToken_falha() {
        runner.withPropertyValues(
                "app.modulos.whatsapp.phoneNumberId=x",
                // accessToken omitido deliberadamente
                "app.modulos.whatsapp.appSecret=x",
                "app.modulos.whatsapp.verifyToken=x",
                "app.modulos.whatsapp.erpCallbackUrl=http://localhost:0/test"
        ).run(context -> assertThat(context).hasFailed()
                .getFailure()
                .hasStackTraceContaining("WHATSAPP_ACCESS_TOKEN nao definida"));
    }

    @Test
    @DisplayName("Boot sem appSecret falha com 'WHATSAPP_APP_SECRET nao definida'")
    void boot_sem_appSecret_falha() {
        runner.withPropertyValues(
                "app.modulos.whatsapp.phoneNumberId=x",
                "app.modulos.whatsapp.accessToken=x",
                // appSecret omitido deliberadamente
                "app.modulos.whatsapp.verifyToken=x",
                "app.modulos.whatsapp.erpCallbackUrl=http://localhost:0/test"
        ).run(context -> assertThat(context).hasFailed()
                .getFailure()
                .hasStackTraceContaining("WHATSAPP_APP_SECRET nao definida"));
    }

    @Test
    @DisplayName("Boot sem verifyToken falha com 'WHATSAPP_VERIFY_TOKEN nao definida'")
    void boot_sem_verifyToken_falha() {
        runner.withPropertyValues(
                "app.modulos.whatsapp.phoneNumberId=x",
                "app.modulos.whatsapp.accessToken=x",
                "app.modulos.whatsapp.appSecret=x",
                // verifyToken omitido deliberadamente
                "app.modulos.whatsapp.erpCallbackUrl=http://localhost:0/test"
        ).run(context -> assertThat(context).hasFailed()
                .getFailure()
                .hasStackTraceContaining("WHATSAPP_VERIFY_TOKEN nao definida"));
    }

    @Test
    @DisplayName("Boot sem erpCallbackUrl falha com 'WHATSAPP_ERP_CALLBACK_URL nao definida'")
    void boot_sem_erpCallbackUrl_falha() {
        runner.withPropertyValues(
                "app.modulos.whatsapp.phoneNumberId=x",
                "app.modulos.whatsapp.accessToken=x",
                "app.modulos.whatsapp.appSecret=x",
                "app.modulos.whatsapp.verifyToken=x"
                // erpCallbackUrl omitido deliberadamente
        ).run(context -> assertThat(context).hasFailed()
                .getFailure()
                .hasStackTraceContaining("WHATSAPP_ERP_CALLBACK_URL nao definida"));
    }

    // ---------------------------------------------------------------------
    // toString() mascara secrets (CFG-03)
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

        // Secrets NUNCA podem aparecer no toString — operador da ERPKit nao deve ver
        // accessToken/appSecret/verifyToken em log de erro de bind do Spring.
        assertThat(s)
                .doesNotContain("access-real-xyz")
                .doesNotContain("secret-real-xyz")
                .doesNotContain("verify-real-xyz");

        // Mascaras explicitas — exatamente 3 ocorrencias (uma por secret).
        long redactedCount = s.split("\\[REDACTED]", -1).length - 1;
        assertThat(redactedCount).as("Esperado [REDACTED] 3x em toString()").isEqualTo(3);

        // Nao-secrets podem (e devem) aparecer para ajudar debugging.
        assertThat(s).contains("phone-real").contains("http://erp/callback");
    }
}
