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
 * do Spring Boot e que o default {@code callbackTimeout = PT5S} esta intacto.
 *
 * <p>Os 5 testes de fail-fast (ausencia de cada campo) ficam em
 * {@link WhatsAppPropertiesValidationTest} usando ApplicationContextRunner — mais leve,
 * sem subir contexto Spring inteiro.
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
        assertThat(properties.getCallbackTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
