package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.EstadoConversa;
import br.com.erpkit.whatsapp.repository.EstadoConversaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final EstadoConversaRepository repository;

    public EstadoConversaService(EstadoConversaRepository repository) {
        this.repository = repository;
    }

    /** True se a conversa esta sob atendimento humano (bot pausado). */
    @Transactional(readOnly = true)
    public boolean estaEmAtendimento(String telefone) {
        return repository.findById(telefone).map(EstadoConversa::isEmAtendimento).orElse(false);
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
