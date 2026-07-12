package br.com.erpkit.whatsapp.dto;

/**
 * Payload do callback POST {@code /api/modulos/whatsapp/comando} ao ERP (D-06 + ROU-02).
 *
 * <p>Record imutavel. Jackson auto-serializa para JSON via convertor padrao do
 * Spring Boot 3 (Boot 3 + Jackson 2.18 suportam record nativamente como wire type
 * de OUTPUT, sem precisar de getters/setters explicitos — diferente do uso de
 * INPUT externo onde a convencao do monorepo prefere POJO).
 *
 * <p>Wave 4 ({@code ErpCallbackClient.despachar}) usa este DTO no body do POST com
 * {@code @CircuitBreaker(name="erp-callback")} + {@code @Retry(name="erp-callback")}.
 *
 * @param telefone        {@code wa_id} EXATO do Meta (digitos, SEM strip do 9o digito) — o ERP
 *                        usa este numero para responder; a Cloud API exige o mesmo wa_id do inbound
 * @param comando         keyword extraida (ex: "orcamento", "aprovar_42", "document")
 * @param payload         conteudo cru da mensagem (texto, "id|title", filename) — pode ser {@code null}
 * @param idCliente       id_cliente_erp resolvido — {@code null} se cliente nao mapeado
 * @param mediaBase64     bytes da media em base64 — {@code null} se sem media ou URL Meta expirou
 * @param mediaMimeType   MIME do arquivo — {@code null} se sem media
 * @param mediaFilename   nome original — {@code null} se sem media
 * @param assistente      persona do assistente (nome/emoji/tom/saudacao) — bloco ADITIVO/opcional
 *                        que o ERP usa para renderizar as respostas; ERP antigo ignora. {@code null}
 *                        quando indisponivel
 */
public record ComandoCallbackDTO(
    String telefone,
    String comando,
    String payload,
    Long idCliente,
    String mediaBase64,
    String mediaMimeType,
    String mediaFilename,
    AssistentePersonaDTO assistente
) {
    /**
     * Construtor de compatibilidade (7 args, sem persona) — mantem os call sites
     * anteriores ao bloco {@code assistente} compilando ({@code assistente = null}).
     */
    public ComandoCallbackDTO(String telefone, String comando, String payload, Long idCliente,
                              String mediaBase64, String mediaMimeType, String mediaFilename) {
        this(telefone, comando, payload, idCliente, mediaBase64, mediaMimeType, mediaFilename, null);
    }
}
