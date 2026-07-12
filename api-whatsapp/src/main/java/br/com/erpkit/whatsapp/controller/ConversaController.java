package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.whatsapp.dto.ConversaDetalheResponse;
import br.com.erpkit.whatsapp.dto.ConversaResumoResponse;
import br.com.erpkit.whatsapp.dto.MensagemResponse;
import br.com.erpkit.whatsapp.service.InboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de LEITURA do Inbox/Chat para o app do atendente, sob
 * {@code /api/whatsapp/conversas} — protegidos pelo {@code WhatsappAuthFilter}
 * (Bearer JWT do atendente; ou X-API-Key service-to-service). Somente leitura:
 * nao alteram schema nem estado.
 *
 * <p><b>Thin wrapper</b> (padrao do monorepo): valida paginacao, delega ao
 * {@link InboxService}, devolve {@code ResponseEntity}. A serializacao de {@link Page}
 * segue o default do Spring Data (campos {@code content}, {@code totalElements},
 * {@code totalPages}, {@code number}, {@code size}, ...). O modulo nao tem wrapper
 * {@code PageResponse} proprio.
 *
 * <p><b>Paginacao:</b> {@code page} (0-based, default 0) e {@code size} (default 30,
 * teto 100 para proteger o backend). Passados como {@code @RequestParam} simples.
 */
@RestController
@RequestMapping("/api/whatsapp/conversas")
public class ConversaController {

    private static final int SIZE_DEFAULT = 30;
    private static final int SIZE_MAX = 100;

    private final InboxService inboxService;

    public ConversaController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    /** Lista de conversas (uma por telefone) ordenada por ultima atividade DESC. */
    @GetMapping
    public ResponseEntity<Page<ConversaResumoResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(inboxService.listarConversas(PageRequest.of(sanitizarPage(page), sanitizarSize(size))));
    }

    /** Historico de mensagens de um telefone, ordem cronologica (ASC), paginado. */
    @GetMapping("/{telefone}/mensagens")
    public ResponseEntity<Page<MensagemResponse>> mensagens(
            @PathVariable String telefone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(
                inboxService.listarMensagens(telefone, PageRequest.of(sanitizarPage(page), sanitizarSize(size))));
    }

    /** Detalhe do contato + estado da janela 24h. 404 se a conversa nao existe. */
    @GetMapping("/{telefone}")
    public ResponseEntity<ConversaDetalheResponse> detalhe(@PathVariable String telefone) {
        ConversaDetalheResponse dto = inboxService.detalhe(telefone);
        if (dto == null) {
            throw new ModuloException("Conversa nao encontrada para o telefone informado", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(dto);
    }

    /** Handoff: a recepcao assume a conversa — o bot para de responder aquele numero. */
    @PostMapping("/{telefone}/assumir")
    public ResponseEntity<Void> assumir(@PathVariable String telefone) {
        inboxService.assumirConversa(telefone);
        return ResponseEntity.ok().build();
    }

    /** Encerra o atendimento humano — o bot volta a responder. */
    @PostMapping("/{telefone}/encerrar")
    public ResponseEntity<Void> encerrar(@PathVariable String telefone) {
        inboxService.encerrarConversa(telefone);
        return ResponseEntity.ok().build();
    }

    private int sanitizarPage(int page) {
        return Math.max(page, 0);
    }

    private int sanitizarSize(int size) {
        if (size < 1) {
            return SIZE_DEFAULT;
        }
        return Math.min(size, SIZE_MAX);
    }
}
