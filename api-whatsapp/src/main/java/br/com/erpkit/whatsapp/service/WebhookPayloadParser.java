package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ChangeDTO;
import br.com.erpkit.whatsapp.dto.EntryDTO;
import br.com.erpkit.whatsapp.dto.InteractiveDTO;
import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.MessageDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.dto.ReplyDTO;
import br.com.erpkit.whatsapp.dto.StatusDTO;
import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.dto.ValueDTO;
import br.com.erpkit.whatsapp.dto.WebhookPayloadDTO;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import br.com.erpkit.whatsapp.util.TipoMensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser do envelope de webhook do Meta. Extrai {@link MensagemEntranteDTO} e
 * {@link StatusEntranteDTO} de {@code byte[] rawBody} cacheado pelo
 * {@link br.com.erpkit.whatsapp.web.CachedBodyHttpServletRequest}.
 *
 * <p>Tolerante a campos ausentes — Meta envia heartbeats, payloads sem messages,
 * sem statuses, etc. Parser nunca lanca excecao por campo ausente; apenas se o
 * JSON em si for malformado (lanca {@link IOException} via
 * {@link ObjectMapper#readValue}).
 *
 * <p>Tipos desconhecidos (novos do Meta, ou interactive sem button/list reply):
 * {@code tipo = "desconhecido"}, {@code conteudo = null}, {@code mediaId = null}
 * (D-05 + WEB-07).
 *
 * <p>{@code from} do Meta vem ja como digito-only (formato {@code 5547999999999}),
 * mas o parser aplica {@link TelefoneBR#normalizar} antes de retornar — defesa em
 * profundidade, custo zero.
 */
@Service
public class WebhookPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(WebhookPayloadParser.class);

    private final ObjectMapper mapper;

    public WebhookPayloadParser(ObjectMapper mapper) {
        // Spring Boot ja registra um ObjectMapper default
        this.mapper = mapper;
    }

    /**
     * Parseia bytes do body do webhook em uma estrutura tipada com listas separadas
     * de mensagens entrantes e statuses callback.
     *
     * @param rawBody bytes do body (UTF-8) — tipicamente vindos do
     *                {@code CachedBodyHttpServletRequest.getCachedBody()}
     * @return {@link ParsedWebhook} com listas (possivelmente vazias) de mensagens e statuses
     * @throws IOException se o JSON for malformado (Jackson lanca
     *                     {@code JsonProcessingException} que extends {@code IOException})
     */
    public ParsedWebhook extrair(byte[] rawBody) throws IOException {
        WebhookPayloadDTO payload = mapper.readValue(rawBody, WebhookPayloadDTO.class);

        List<MensagemEntranteDTO> mensagens = new ArrayList<>();
        List<StatusEntranteDTO> statuses = new ArrayList<>();

        if (payload == null || payload.getEntry() == null) {
            log.debug("Webhook payload sem 'entry' — ignorando (heartbeat ou keepalive)");
            return new ParsedWebhook(mensagens, statuses);
        }

        for (EntryDTO entry : payload.getEntry()) {
            if (entry.getChanges() == null) continue;
            for (ChangeDTO change : entry.getChanges()) {
                ValueDTO value = change.getValue();
                if (value == null) continue;

                if (value.getMessages() != null) {
                    for (MessageDTO msg : value.getMessages()) {
                        mensagens.add(extrairMensagem(msg));
                    }
                }
                if (value.getStatuses() != null) {
                    for (StatusDTO st : value.getStatuses()) {
                        statuses.add(extrairStatus(st));
                    }
                }
            }
        }

        log.debug("Webhook parseado: {} mensagens, {} statuses", mensagens.size(), statuses.size());
        return new ParsedWebhook(mensagens, statuses);
    }

    private MensagemEntranteDTO extrairMensagem(MessageDTO msg) {
        String wamid = msg.getId();
        String telefone = TelefoneBR.normalizar(msg.getFrom());
        String tipo = mapTipo(msg);
        String conteudo = extrairConteudo(msg, tipo);
        String mediaId = extrairMediaId(msg);
        return new MensagemEntranteDTO(wamid, telefone, tipo, conteudo, mediaId);
    }

    /** Mapa {@code msg.type} -> constants {@link TipoMensagem}. Desconhecidos viram DESCONHECIDO. */
    private String mapTipo(MessageDTO msg) {
        String t = msg.getType();
        if (t == null) return TipoMensagem.DESCONHECIDO;
        switch (t) {
            case "text":     return TipoMensagem.TEXT;
            case "document": return TipoMensagem.DOCUMENT;
            case "image":    return TipoMensagem.IMAGE;
            case "audio":    return TipoMensagem.AUDIO;
            case "interactive":
                InteractiveDTO it = msg.getInteractive();
                if (it == null || it.getType() == null) return TipoMensagem.DESCONHECIDO;
                return switch (it.getType()) {
                    case "button_reply" -> TipoMensagem.INTERACTIVE_BUTTON;
                    case "list_reply"   -> TipoMensagem.INTERACTIVE_LIST;
                    default              -> TipoMensagem.DESCONHECIDO;
                };
            default:
                log.debug("Tipo desconhecido do Meta: {} (wamid={})", t, msg.getId());
                return TipoMensagem.DESCONHECIDO;
        }
    }

    private String extrairConteudo(MessageDTO msg, String tipo) {
        return switch (tipo) {
            case TipoMensagem.TEXT ->
                msg.getText() == null ? null : msg.getText().getBody();
            case TipoMensagem.INTERACTIVE_BUTTON -> {
                ReplyDTO r = msg.getInteractive() == null ? null : msg.getInteractive().getButtonReply();
                yield r == null ? null : r.getId() + "|" + r.getTitle();
            }
            case TipoMensagem.INTERACTIVE_LIST -> {
                ReplyDTO r = msg.getInteractive() == null ? null : msg.getInteractive().getListReply();
                yield r == null ? null : r.getId() + "|" + r.getTitle();
            }
            case TipoMensagem.DOCUMENT ->
                msg.getDocument() == null ? null : msg.getDocument().getFilename();
            case TipoMensagem.IMAGE ->
                msg.getImage() == null ? null : msg.getImage().getMimeType();
            case TipoMensagem.AUDIO ->
                msg.getAudio() == null ? null : msg.getAudio().getMimeType();
            default -> null;  // desconhecido
        };
    }

    private String extrairMediaId(MessageDTO msg) {
        if (msg.getDocument() != null) return msg.getDocument().getId();
        if (msg.getImage() != null)    return msg.getImage().getId();
        if (msg.getAudio() != null)    return msg.getAudio().getId();
        return null;
    }

    private StatusEntranteDTO extrairStatus(StatusDTO st) {
        return new StatusEntranteDTO(
            st.getId(),
            st.getStatus(),
            TelefoneBR.normalizar(st.getRecipientId())
        );
    }
}
