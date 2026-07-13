package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.AssistentePersonaDTO;
import br.com.erpkit.whatsapp.dto.ComandoCallbackDTO;
import br.com.erpkit.whatsapp.dto.DesfechoCallbackDTO;
import br.com.erpkit.whatsapp.dto.MetaMediaResultado;
import br.com.erpkit.whatsapp.event.MensagemPersistidaEvent;
import br.com.erpkit.whatsapp.model.ClienteZap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Base64;
import java.util.Optional;

/**
 * Listener async do {@link MensagemPersistidaEvent} — orquestrador da Phase 3 D-01.
 *
 * <p><b>Sequencia (D-01 + D-04 + ROU-05):</b>
 * <ol>
 *   <li>Media download (PRIMEIRA acao — URL Meta expira em 5min, PITFALLS C-08)</li>
 *   <li>Identificar cliente (auto-create id_cliente_erp=null se nao existe — Phase 2)</li>
 *   <li>Atualizar ultima_mensagem_em REQUIRES_NEW (preserva trava 24h Phase 4)</li>
 *   <li>Extrair comando (texto -&gt; primeira palavra; interactive -&gt; id; media -&gt; tipo)</li>
 *   <li>Dispatch ERP via {@link ErpCallbackClient} (Resilience4j cuida de retry/CB)</li>
 * </ol>
 *
 * <p><b>Try/catch em cada step:</b> log.warn (media) ou log.error (others) +
 * return early para identificar/atualizar (sem dados nao da pra continuar).
 * Listener NUNCA propaga — async, sem ninguem para receber a excecao.
 *
 * <p><b>{@link Async}("whatsappTaskExecutor"):</b> roda em pool dedicado
 * (Wave 1 {@code AsyncConfig}). Test profile (Wave 6) sobrescreve com
 * SyncTaskExecutor para tests E2E manterem assertions DB sincronas.
 *
 * <p><b>{@link TransactionalEventListener}({@code AFTER_COMMIT}):</b> garante que
 * o listener nao dispare se INSERT em mensagens_log fez rollback — invariant
 * critico (PITFALLS C-05: ack-first sem falsos positivos no ERP).
 *
 * <p><b>Cross-bean call:</b> {@link ClienteZapService#atualizarUltimaMensagemEm}
 * usa {@code @Transactional(REQUIRES_NEW)}. Como este listener e um bean diferente,
 * a chamada passa pelo proxy AOP do Spring e a propagacao funciona corretamente
 * (A3 RESEARCH).
 */
@Component
public class MensagemAsyncListener {

    private static final Logger log = LoggerFactory.getLogger(MensagemAsyncListener.class);

    private final MetaMediaClient metaMediaClient;
    private final ClienteZapService clienteZapService;
    private final ComandoExtractor comandoExtractor;
    private final ErpCallbackClient erpCallbackClient;
    private final MensagemLogService mensagemLogService;
    private final AssistenteService assistenteService;
    private final EstadoConversaService estadoConversaService;
    private final WhatsAppCloudClient cloudClient;

    public MensagemAsyncListener(MetaMediaClient metaMediaClient,
                                 ClienteZapService clienteZapService,
                                 ComandoExtractor comandoExtractor,
                                 ErpCallbackClient erpCallbackClient,
                                 MensagemLogService mensagemLogService,
                                 AssistenteService assistenteService,
                                 EstadoConversaService estadoConversaService,
                                 WhatsAppCloudClient cloudClient) {
        this.metaMediaClient = metaMediaClient;
        this.clienteZapService = clienteZapService;
        this.comandoExtractor = comandoExtractor;
        this.erpCallbackClient = erpCallbackClient;
        this.mensagemLogService = mensagemLogService;
        this.assistenteService = assistenteService;
        this.estadoConversaService = estadoConversaService;
        this.cloudClient = cloudClient;
    }

    /**
     * Roda async apos commit do INSERT em mensagens_log (Phase 2 IdempotencyService).
     * Sequencia D-01: media -&gt; identificar -&gt; atualizar -&gt; comando -&gt; callback ERP.
     *
     * <p>Cada step captura excecao + log error/warn, NAO propaga.
     */
    @Async("whatsappTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aoMensagemPersistida(MensagemPersistidaEvent event) {
        log.debug("Listener async iniciando: wamid={}", event.wamid());

        // [1] media download — PRIMEIRA acao (PITFALLS C-08: URL Meta expira em 5min)
        String mediaBase64 = null;
        String mediaMimeType = null;
        String mediaFilename = null;
        if (event.mediaId() != null) {
            try {
                Optional<MetaMediaResultado> media = metaMediaClient.baixar(event.mediaId());
                if (media.isPresent()) {
                    mediaBase64 = Base64.getEncoder().encodeToString(media.get().bytes());
                    mediaMimeType = media.get().mimeType();
                    mediaFilename = media.get().filename();
                    log.debug("Media baixada: wamid={} mimeType={} bytes={}",
                              event.wamid(), mediaMimeType, media.get().bytes().length);
                } else {
                    log.warn("Media expirada/nao-disponivel: wamid={} mediaId={} — prosseguindo sem bytes",
                             event.wamid(), event.mediaId());
                }
            } catch (Exception e) {
                log.warn("Erro baixando media: wamid={} mediaId={}: {}",
                         event.wamid(), event.mediaId(), e.getMessage());
                // prosseguir sem bytes
            }
        }

        // [2] identificar cliente
        ClienteZap cliente;
        try {
            cliente = clienteZapService.identificar(event.telefone());
        } catch (Exception e) {
            log.error("Falha identificando cliente: telefone={}: {}",
                      event.telefone(), e.getMessage(), e);
            return;
        }

        // [3] atualizar ultima_mensagem_em (REQUIRES_NEW — commit imediato; PITFALLS C-01)
        try {
            clienteZapService.atualizarUltimaMensagemEm(event.telefone());
        } catch (Exception e) {
            log.error("Falha atualizando ultima_mensagem_em: telefone={}: {}",
                      event.telefone(), e.getMessage(), e);
            return;
        }

        // [3.5] HANDOFF: se a conversa esta em atendimento humano, o bot NAO responde — a
        // mensagem ja foi persistida e aparece no inbox; a recepcao trata manualmente.
        try {
            if (estadoConversaService.estaEmAtendimento(event.telefone())) {
                log.info("Conversa em atendimento humano — bot silenciado: telefone={}", event.telefone());
                return;
            }
        } catch (Exception e) {
            log.warn("Falha ao checar estado de atendimento (seguindo com o bot): {}", e.getMessage());
        }

        // [3.6] FORA DO HORARIO: se configurado e a mensagem chega fora do expediente, responde
        // o aviso da persona e NAO processa o bot. A clinica define o horario na tela Assistente;
        // horario vazio = atende 24h (nunca bloqueia).
        try {
            if (assistenteService.estaForaDoHorario()) {
                enviarSeguro(event.telefoneWaId(), assistenteService.mensagemForaHorario());
                log.info("Fora do horario de atendimento — aviso enviado: telefone={}", event.telefone());
                return;
            }
        } catch (Exception e) {
            log.warn("Falha no check de horario (seguindo com o bot): {}", e.getMessage());
        }

        // [3.1] resolver id_cliente_erp no ERP se ainda NULL (D-07). Best-effort: falha
        // aqui NAO interrompe o fluxo — o callback segue com id NULL (comportamento atual).
        Long idClienteErp = cliente.getIdClienteErp();
        if (idClienteErp == null) {
            try {
                Long resolvido = erpCallbackClient.resolverIdCliente(event.telefone());
                if (resolvido != null && clienteZapService.vincularClienteErp(event.telefone(), resolvido)) {
                    idClienteErp = resolvido;
                }
            } catch (Exception e) {
                log.warn("Falha ao resolver/vincular id_cliente_erp: telefone={}: {}",
                         event.telefone(), e.getMessage());
            }
        }

        // [4] extrair comando
        String comando = comandoExtractor.extrair(event.tipo(), event.conteudo());

        // [4.1] enriquecer a linha de entrada (V7 §12): id_cliente_erp + comando + evento_em
        // (ts real da Meta). Roda mesmo sem comando. Best-effort — nao interrompe o dispatch.
        try {
            mensagemLogService.enriquecerEntrada(event.wamid(), idClienteErp, comando, event.timestamp());
        } catch (Exception e) {
            log.warn("Falha ao enriquecer entrada wamid={}: {}", event.wamid(), e.getMessage());
        }

        if (comando == null) {
            log.debug("Sem comando para tipo={} — skip dispatch ERP", event.tipo());
            return;
        }

        // [4.2] persona do assistente para o bloco aditivo do callback — best-effort
        // (o dispatch nao pode falhar por causa da persona; ERP recebe null e usa defaults).
        AssistentePersonaDTO assistente = null;
        try {
            assistente = assistenteService.personaParaCallback();
        } catch (Exception e) {
            log.warn("Falha ao carregar persona do assistente (dispatch sem ela): {}", e.getMessage());
        }

        // [5] dispatch ERP (Resilience4j cuida de retry+CB+fallback).
        // telefone = wa_id EXATO do Meta (event.telefoneWaId), NAO o normalizado — o ERP
        // usa este numero para RESPONDER, e a Cloud API exige o mesmo wa_id do inbound
        // (a versao com strip do 9o digito falha no envio — validado no teste real).
        ComandoCallbackDTO payload = new ComandoCallbackDTO(
            event.telefoneWaId(), comando, event.conteudo(),
            idClienteErp, mediaBase64, mediaMimeType, mediaFilename, assistente, event.wamid()
        );
        try {
            DesfechoCallbackDTO desfecho = erpCallbackClient.despachar(payload);
            // §12 #6: persiste o desfecho do bot (respondido/nao_entendi/sem_resposta/erro).
            // desfecho null = callback falhou (fallback Resilience4j) -> deixa sem resultado.
            if (desfecho != null && desfecho.resultado() != null) {
                mensagemLogService.registrarDesfecho(event.wamid(), desfecho.resultado());
                // Fallback "nao entendi": nenhum handler do ERP casou o comando -> a persona
                // responde a mensagem generica (senao o bot ficaria mudo p/ texto livre).
                if ("nao_entendi".equals(desfecho.resultado())) {
                    enviarSeguro(event.telefoneWaId(), assistenteService.mensagemNaoEntendi());
                }
            }
        } catch (Exception e) {
            // Resilience4j fallbackMethod ja capturou e logou. Catch-all defensivo
            // para qualquer excecao que escape (ex: bug, OOM em base64).
            log.error("Falha definitiva no callback ERP: wamid={} comando={}: {}",
                      event.wamid(), comando, e.getMessage(), e);
        }
    }

    /** Envia um texto ao paciente sem propagar falha (best-effort; respeita a janela 24h). */
    private void enviarSeguro(String telefoneWaId, String texto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        try {
            cloudClient.enviarTexto(telefoneWaId, texto);
        } catch (Exception e) {
            log.warn("Falha ao enviar mensagem automatica (telefone={}): {}", telefoneWaId, e.getMessage());
        }
    }
}
