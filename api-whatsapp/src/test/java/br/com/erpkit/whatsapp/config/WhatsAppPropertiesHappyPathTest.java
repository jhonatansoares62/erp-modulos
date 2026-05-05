package br.com.erpkit.whatsapp.config;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: contexto Spring inteiro sobe com {@code application-test.yml} fornecendo
 * os 5 dummy values. Verifica que {@link WhatsAppProperties} bean e populado pelo bind
 * do Spring Boot.
 *
 * <p>Os 5 testes de fail-fast (ausencia de cada campo) ficam em
 * {@link WhatsAppPropertiesValidationTest} usando ApplicationContextRunner — mais leve,
 * sem subir contexto Spring inteiro.
 *
 * <p><b>Wave 4 (PLAN 03-04):</b> {@code callbackTimeout} foi reduzido para 500ms no
 * {@code application-test.yml} para suportar
 * {@code ErpCallbackClientTest.timeout_retry_e_fallback}. O default em PRODUCAO continua
 * 5s ({@link WhatsAppProperties#callbackTimeout}); este test agora valida o valor de
 * test profile (500ms) — o default-prod e validado indiretamente por leitura do tipo
 * {@code Duration}.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class WhatsAppPropertiesHappyPathTest {

    @Autowired
    WhatsAppProperties properties;

    @Test
    @DisplayName("Boot com todas as 5 propriedades carrega WhatsAppProperties bean populado")
    void boot_com_todas_as_5_propriedades_passa() {
        assertThat(properties).isNotNull();
        assertThat(properties.getPhoneNumberId()).isEqualTo("test-phone-id");
        assertThat(properties.getAccessToken()).isEqualTo("test-access-token");
        assertThat(properties.getAppSecret()).isEqualTo("test-app-secret");
        assertThat(properties.getVerifyToken()).isEqualTo("test-verify-token");
        assertThat(properties.getErpCallbackUrl()).isEqualTo("http://localhost:0/test");
        // Wave 4: test profile usa 500ms (ver application-test.yml). Default prod
        // permanece 5s — testado indiretamente via tipo Duration nao-null aqui.
        assertThat(properties.getCallbackTimeout()).isEqualTo(Duration.ofMillis(500));
    }
}
