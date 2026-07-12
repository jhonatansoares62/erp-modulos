package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ConversaDetalheResponse;
import br.com.erpkit.whatsapp.dto.ConversaResumoResponse;
import br.com.erpkit.whatsapp.dto.MensagemResponse;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import br.com.erpkit.whatsapp.util.TipoMensagem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Camada de LEITURA do Inbox/Chat para o app do atendente (nenhuma escrita — sem
 * alterar schema). Projeta {@code clientes_zap} + {@code mensagens_log} nos DTOs de
 * conversa/mensagem consumidos pela tela.
 *
 * <p><b>Janela 24h (fonte da verdade):</b> derivada de
 * {@code clientes_zap.ultima_mensagem_em} — a ultima mensagem ENTRANTE, gravada com o
 * relogio do banco ({@code NOW()}) via PER-07. E exatamente o campo que o
 * {@link WindowEnforcementService} usa para a trava de envio; reusar aqui mantem a UI
 * coerente com o que o backend permite enviar. {@code janelaExpiraEm =
 * ultima_mensagem_em + 24h}; {@code janelaAberta = agora < janelaExpiraEm}. A tabela
 * {@code estado_conversa} (V4) e um placeholder nunca populado — nao e usada.
 *
 * <p><b>Preview:</b> {@code conteudo} varia por tipo (ver {@code WebhookPayloadParser}):
 * texto e o corpo; {@code interactive_*} vem como {@code id|titulo}; {@code document} e
 * o nome do arquivo; {@code image}/{@code audio} e o mime-type. {@link #preview} produz
 * um rotulo amigavel e curto (~80 chars) para exibir no card/bolha.
 */
@Service
public class InboxService {

    /** Igual a {@code WindowEnforcementService.JANELA} — mantem UI e trava coerentes. */
    private static final Duration JANELA = Duration.ofHours(24);
    private static final int PREVIEW_MAX = 80;

    private final MensagemLogRepository mensagemRepository;
    private final ClienteZapRepository clienteRepository;
    private final EstadoConversaService estadoConversaService;

    public InboxService(MensagemLogRepository mensagemRepository,
                        ClienteZapRepository clienteRepository,
                        EstadoConversaService estadoConversaService) {
        this.mensagemRepository = mensagemRepository;
        this.clienteRepository = clienteRepository;
        this.estadoConversaService = estadoConversaService;
    }

    /**
     * Lista de conversas (uma por telefone) ordenada por ultima atividade DESC,
     * paginada. Para cada telefone da pagina resolve a ultima mensagem (preview/
     * direcao) e o estado da janela 24h.
     */
    @Transactional(readOnly = true)
    public Page<ConversaResumoResponse> listarConversas(Pageable pageable) {
        Instant agora = Instant.now();
        return mensagemRepository.listarConversas(pageable)
                .map(linha -> montarResumo(linha, agora));
    }

    private ConversaResumoResponse montarResumo(Object[] linha, Instant agora) {
        String telefone = (String) linha[0];
        Instant ultimaAtividade = (Instant) linha[1];
        long total = (Long) linha[2];

        ConversaResumoResponse dto = new ConversaResumoResponse();
        dto.setTelefone(telefone);
        dto.setNome(null); // clientes_zap nao armazena nome do contato (ainda)
        dto.setUltimaMensagemEm(ultimaAtividade);
        dto.setTotalMensagens(total);

        mensagemRepository.findFirstByTelefoneOrderByCriadoEmDesc(telefone).ifPresent(ultima -> {
            dto.setUltimaMensagemPreview(preview(ultima.getTipo(), ultima.getConteudo()));
            dto.setUltimaDirecao(rotuloDirecao(ultima.getDirecao()));
        });

        aplicarJanela(clienteRepository.findByTelefone(telefone), agora,
                dto::setJanelaAberta, dto::setJanelaExpiraEm);
        dto.setEmAtendimento(estadoConversaService.estaEmAtendimento(telefone));
        return dto;
    }

    /** Assume a conversa (bot pausado). Normaliza o telefone pra bater com o guard do inbound. */
    @Transactional
    public void assumirConversa(String telefone) {
        estadoConversaService.assumir(TelefoneBR.normalizar(telefone));
    }

    /** Encerra o atendimento humano (bot reativado). */
    @Transactional
    public void encerrarConversa(String telefone) {
        estadoConversaService.encerrar(TelefoneBR.normalizar(telefone));
    }

    /**
     * Historico cronologico (ASC) de um telefone, paginado. O telefone e normalizado
     * (mesma regra do armazenamento) antes da consulta.
     */
    @Transactional(readOnly = true)
    public Page<MensagemResponse> listarMensagens(String telefone, Pageable pageable) {
        String normalizado = TelefoneBR.normalizar(telefone);
        return mensagemRepository.findByTelefoneOrderByCriadoEmAsc(normalizado, pageable)
                .map(this::montarMensagem);
    }

    private MensagemResponse montarMensagem(MensagemLog m) {
        MensagemResponse dto = new MensagemResponse();
        dto.setId(m.getId());
        dto.setDirecao(rotuloDirecao(m.getDirecao()));
        dto.setTipo(m.getTipo());
        dto.setConteudo(m.getConteudo());
        dto.setPreview(preview(m.getTipo(), m.getConteudo()));
        // timestamp de exibicao: evento na Meta quando houver, senao criado no banco
        dto.setTimestamp(m.getEventoEm() != null ? m.getEventoEm() : m.getCriadoEm());
        dto.setStatus(m.getStatus());
        dto.setResultado(m.getResultado());
        dto.setWamid(m.getWamid());
        return dto;
    }

    /**
     * Detalhe do contato + estado da janela. Retorna {@code null} se o telefone nao
     * tem nenhuma mensagem nem registro em {@code clientes_zap} (controller vira 404).
     */
    @Transactional(readOnly = true)
    public ConversaDetalheResponse detalhe(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        Optional<ClienteZap> cliente = clienteRepository.findByTelefone(normalizado);
        long total = mensagemRepository.countByTelefone(normalizado);

        if (cliente.isEmpty() && total == 0) {
            return null;
        }

        ConversaDetalheResponse dto = new ConversaDetalheResponse();
        dto.setTelefone(normalizado);
        dto.setNome(null);
        dto.setIdClienteErp(cliente.map(ClienteZap::getIdClienteErp).orElse(null));
        dto.setUltimaMensagemEm(cliente.map(ClienteZap::getUltimaMensagemEm).orElse(null));
        dto.setTotalMensagens(total);
        aplicarJanela(cliente, Instant.now(), dto::setJanelaAberta, dto::setJanelaExpiraEm);
        return dto;
    }

    // ── Helpers ──

    /**
     * Calcula a janela 24h a partir de {@code clientes_zap.ultima_mensagem_em}
     * (ultima ENTRADA). Sem registro / sem entrada -> fechada e expira null.
     */
    private void aplicarJanela(Optional<ClienteZap> cliente, Instant agora,
                               java.util.function.Consumer<Boolean> setAberta,
                               java.util.function.Consumer<Instant> setExpira) {
        Instant ultimaEntrada = cliente.map(ClienteZap::getUltimaMensagemEm).orElse(null);
        if (ultimaEntrada == null) {
            setAberta.accept(false);
            setExpira.accept(null);
            return;
        }
        Instant expira = ultimaEntrada.plus(JANELA);
        setAberta.accept(agora.isBefore(expira));
        setExpira.accept(expira);
    }

    private String rotuloDirecao(Direcao direcao) {
        if (direcao == null) {
            return null;
        }
        return direcao == Direcao.in ? "ENTRADA" : "SAIDA";
    }

    /**
     * Rotulo amigavel/curto para exibir. Texto e truncado em ~80 chars; tipos de
     * midia/interativos viram rotulos ("[documento] arquivo.pdf", "[imagem]", etc.).
     */
    private String preview(String tipo, String conteudo) {
        if (tipo == null) {
            return truncar(conteudo);
        }
        return switch (tipo) {
            case TipoMensagem.TEXT -> truncar(conteudo);
            case TipoMensagem.INTERACTIVE_BUTTON, TipoMensagem.INTERACTIVE_LIST ->
                "[resposta] " + truncar(tituloInterativo(conteudo));
            case TipoMensagem.DOCUMENT ->
                conteudo == null || conteudo.isBlank() ? "[documento]" : "[documento] " + truncar(conteudo);
            case TipoMensagem.IMAGE -> "[imagem]";
            case TipoMensagem.AUDIO -> "[audio]";
            default -> conteudo == null || conteudo.isBlank() ? "[mensagem]" : truncar(conteudo);
        };
    }

    /** {@code interactive_*} armazena {@code id|titulo}; exibe so o titulo. */
    private String tituloInterativo(String conteudo) {
        if (conteudo == null) {
            return null;
        }
        int barra = conteudo.indexOf('|');
        return barra >= 0 ? conteudo.substring(barra + 1) : conteudo;
    }

    private String truncar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.strip();
        if (t.length() <= PREVIEW_MAX) {
            return t;
        }
        return t.substring(0, PREVIEW_MAX - 1).stripTrailing() + "…"; // reticencias
    }
}
