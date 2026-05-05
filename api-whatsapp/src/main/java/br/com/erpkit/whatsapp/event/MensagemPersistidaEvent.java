package br.com.erpkit.whatsapp.event;

/**
 * Event imutavel disparado APOS persistencia bem-sucedida em mensagens_log.
 *
 * <p>Listener async ({@code MensagemAsyncListener}, Wave 5) consome em pool dedicado
 * ({@code whatsappTaskExecutor}). {@code @TransactionalEventListener(AFTER_COMMIT)}
 * garante que o evento NAO dispara se o INSERT for rolled back — alinhado com D-01
 * do CONTEXT (PITFALLS C-05: ack-first sem falsos positivos no ERP).
 *
 * <p>Wave 2 entrega apenas o tipo; Wave 5 publica via {@code ApplicationEventPublisher}
 * dentro de {@code MensagemService.processarWebhook}.
 *
 * @param wamid       ID unico do Meta (correlacao em logs estruturados)
 * @param telefone    JA NORMALIZADO via {@code TelefoneBR} (parser Phase 2 fez antes da persistencia)
 * @param tipo        constants de {@code TipoMensagem} (text, interactive_button, document, etc.)
 * @param conteudo    payload extraido (texto cru, "id|title", filename) — pode ser {@code null}
 * @param mediaId     id Meta para media (document/image/audio) — {@code null} se sem media
 * @param idClienteErp campo reservado, sempre {@code null} neste evento — listener busca via {@code ClienteZapService}
 */
public record MensagemPersistidaEvent(
    String wamid,
    String telefone,
    String tipo,
    String conteudo,
    String mediaId,
    Long idClienteErp
) { }
