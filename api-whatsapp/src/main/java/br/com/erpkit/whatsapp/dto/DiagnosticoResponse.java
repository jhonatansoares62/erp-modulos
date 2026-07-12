package br.com.erpkit.whatsapp.dto;

/**
 * Diagnostico do modulo para a tela de Testes — resposta de
 * {@code GET /api/whatsapp/diagnostico} (producao, autenticado) e do
 * {@code GET /monitor/diagnostico} (dev/meta). Valida token Meta + alcancabilidade
 * do ERP callback + estado do circuit breaker. Mesmo shape nos dois ambientes.
 *
 * @param phoneNumberId       numero configurado
 * @param metaApiBaseUrl      base URL da Graph API do Meta
 * @param erpCallbackUrl      URL do ERP co-instalado (callback)
 * @param apiKeyConfigurada   {@code true} se a api-key service-to-service esta setada
 * @param circuitBreakerState estado do circuit breaker {@code whatsapp-cloud}
 * @param meta                checagem do token/numero Meta (Graph API)
 * @param erp                 checagem de alcancabilidade do ERP callback
 */
public record DiagnosticoResponse(
        String phoneNumberId,
        String metaApiBaseUrl,
        String erpCallbackUrl,
        boolean apiKeyConfigurada,
        String circuitBreakerState,
        DiagnosticoCheck meta,
        DiagnosticoCheck erp
) {
}
