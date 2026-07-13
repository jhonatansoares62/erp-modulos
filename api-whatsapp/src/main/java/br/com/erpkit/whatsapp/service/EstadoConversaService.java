package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.EstadoConversa;
import br.com.erpkit.whatsapp.repository.EstadoConversaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Handoff de atendimento humano por conversa (telefone). Quando "em atendimento", o bot
 * fica mudo pra aquele numero (guard no {@link MensagemAsyncListener}); a recepcao
 * assume/encerra pelo inbox. GENERICO — nao conhece dominio de ERP.
 */
@Service
public class EstadoConversaService {

    private static final Logger log = LoggerFactory.getLogger(EstadoConversaService.class);

    /** Handoff auto-expira apos este tempo — rede de seguranca se a recepcao esquecer de encerrar. */
    private static final Duration TTL = Duration.ofHours(6);

    private final EstadoConversaRepository repository;

    public EstadoConversaService(EstadoConversaRepository repository) {
        this.repository = repository;
    }

    /**
     * True se a conversa esta sob atendimento humano (bot pausado). Se o handoff esta aberto ha
     * mais que o {@link #TTL} (recepcao esqueceu de encerrar), auto-encerra aqui mesmo — assim o
     * bot NAO fica mudo pra sempre naquele numero: a proxima mensagem do paciente ja volta pro bot.
     */
    @Transactional
    public boolean estaEmAtendimento(String telefone) {
        EstadoConversa e = repository.findById(telefone).orElse(null);
        if (e == null || !e.isEmAtendimento()) {
            return false;
        }
        Instant inicio = e.getIniciadoEm();
        if (inicio != null && inicio.isBefore(Instant.now().minus(TTL))) {
            e.setEmAtendimento(false);
            e.setIniciadoEm(null);
            e.setUltimaAtualizacao(Instant.now());
            repository.save(e);
            log.info("Handoff: conversa {} expirou (>{}h sem encerrar) — bot reativado", telefone, TTL.toHours());
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<EstadoConversa> obterEstado(String telefone) {
        return repository.findById(telefone);
    }

    /** A recepcao (ou o ERP) assume a conversa: pausa o bot. */
    @Transactional
    public void assumir(String telefone) {
        EstadoConversa e = repository.findById(telefone).orElseGet(EstadoConversa::new);
        e.setTelefone(telefone);
        e.setEmAtendimento(true);
        e.setIniciadoEm(Instant.now());
        e.setUltimaAtualizacao(Instant.now());
        repository.save(e);
        log.info("Handoff: conversa {} assumida (bot pausado)", telefone);
    }

    /** Encerra o atendimento humano: o bot volta a responder. */
    @Transactional
    public void encerrar(String telefone) {
        repository.findById(telefone).ifPresent(e -> {
            e.setEmAtendimento(false);
            e.setIniciadoEm(null);
            e.setUltimaAtualizacao(Instant.now());
            repository.save(e);
            log.info("Handoff: atendimento da conversa {} encerrado (bot reativado)", telefone);
        });
    }
}
