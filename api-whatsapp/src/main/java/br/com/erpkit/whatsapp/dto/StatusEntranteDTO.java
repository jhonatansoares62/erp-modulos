package br.com.erpkit.whatsapp.dto;

import java.time.Instant;

/**
 * Status callback do Meta (sent/delivered/read/failed) JA extraido e normalizado.
 *
 * <p>V7 (observabilidade §12): antes so {@code wamid/status/telefone}; agora carrega
 * o timestamp do evento e os dados de conversa/pricing/erro que a Meta manda — para
 * {@link br.com.erpkit.whatsapp.service.StatusEntregaService} persistir por wamid.
 *
 * @param wamid          ID do Meta da mensagem original (correlaciona com mensagens_log)
 * @param status         "sent" | "delivered" | "read" | "failed"
 * @param telefone       recipient_id JA NORMALIZADO via TelefoneBR
 * @param timestamp      instante do evento na Meta ({@code null} se ausente/invalido)
 * @param conversationId id da conversa da Meta ({@code null} se ausente)
 * @param conversaOrigem {@code conversation.origin.type} (service/utility/...) ou {@code null}
 * @param categoria      {@code pricing.category} (categoria faturavel) ou {@code null}
 * @param billable       {@code pricing.billable} ou {@code null}
 * @param erroCodigo     {@code errors[0].code} (status=failed) ou {@code null}
 * @param erroTitulo     {@code errors[0].title} (status=failed) ou {@code null}
 */
public record StatusEntranteDTO(
    String wamid,
    String status,
    String telefone,
    Instant timestamp,
    String conversationId,
    String conversaOrigem,
    String categoria,
    Boolean billable,
    Integer erroCodigo,
    String erroTitulo
) { }
