package br.com.erpkit.whatsapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI / Swagger UI do api-whatsapp (QA-05). O SpringDoc ja expoe
 * {@code /swagger-ui.html} e {@code /v3/api-docs} automaticamente (paths liberados
 * pelo {@code ApiKeyFilter}); este bean apenas enriquece o documento com titulo,
 * versao e descricao dos dois grupos de endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI whatsAppOpenAPI(@Value("${modulo.versao:1.0.0}") String versao) {
        return new OpenAPI().info(new Info()
                .title("ERP Kit — API WhatsApp")
                .version(versao)
                .description("""
                        Modulo plugavel de integracao com o WhatsApp Cloud API — reativo puro,
                        custo zero de Meta garantido por design (sem envio de template, trava
                        hard de janela 24h).

                        **Grupos de endpoints:**
                        - `POST/GET /webhook/whatsapp` — webhook do Meta (publico, validado por
                          HMAC-SHA256 no header `X-Hub-Signature-256`).
                        - `POST /api/whatsapp/enviar-*` + `GET /api/whatsapp/status` — endpoints
                          internos do ERP para envio outbound (protegidos por `X-API-Key`).

                        Consuma este servico a partir de um ERP via a lib `lib-whatsapp-client`.""")
                .contact(new Contact().name("ERP Kit").email("suporte@erpkit.com.br"))
                .license(new License().name("Proprietario — ERP Kit")));
    }
}
