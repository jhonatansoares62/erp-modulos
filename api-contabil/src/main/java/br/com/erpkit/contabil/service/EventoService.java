package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.model.EventoRecebido;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.repository.EventoRecebidoRepository;
import br.com.erpkit.shared.exception.ModuloException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Recepção de eventos de negócio. Idempotente por eventoId: reenviar o mesmo evento
 * não gera lançamento duplicado. Se um roteiro casa, posta o lançamento; senão, o
 * evento vira pendência (status sem_regra) com o payload bruto guardado — nunca
 * lançamento silencioso.
 */
@Service
public class EventoService {

    private static final Logger log = LoggerFactory.getLogger(EventoService.class);

    private final EventoRecebidoRepository eventoRepository;
    private final RoteiroService roteiroService;
    private final LancamentoService lancamentoService;
    private final ObjectMapper objectMapper;

    public EventoService(EventoRecebidoRepository eventoRepository,
                         RoteiroService roteiroService,
                         LancamentoService lancamentoService,
                         ObjectMapper objectMapper) {
        this.eventoRepository = eventoRepository;
        this.roteiroService = roteiroService;
        this.lancamentoService = lancamentoService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventoRecebidoResponse receber(EventoContabilRequest req) {
        UUID id = parseId(req.getEventoId());

        // Idempotência: se já processamos este eventoId, devolve o resultado anterior.
        Optional<EventoRecebido> existente = eventoRepository.findById(id);
        if (existente.isPresent()) {
            EventoRecebido e = existente.get();
            log.info("Evento {} já recebido (status={}) — idempotente, sem duplicar", id, e.getStatus());
            return new EventoRecebidoResponse(id.toString(), e.getStatus(), e.getLancamentoId());
        }

        EventoRecebido evento = new EventoRecebido();
        evento.setId(id);
        evento.setTipo(req.getTipo());
        evento.setOrigem(req.getOrigem());
        evento.setEmpresaId(req.getEmpresaId());
        evento.setDataEvento(req.getDataEvento());
        evento.setValorCentavos(req.getValorCentavos());
        evento.setPayload(serializar(req));
        evento.setStatus("pendente");
        // Persiste o evento ANTES de postar o lançamento: a FK lancamento.origem_evento_id
        // referencia evento_recebido(id), então o alvo precisa existir primeiro (saveAndFlush).
        eventoRepository.saveAndFlush(evento);

        Optional<RegraLancamento> regra = roteiroService.casar(req.getTipo(), req.getContexto(), req.getDataEvento());
        if (regra.isPresent()) {
            Lancamento lanc = lancamentoService.postarDeEvento(req, regra.get());
            evento.setStatus("processado");
            evento.setLancamentoId(lanc.getId());
            evento.setProcessadoEm(Instant.now());
            log.info("Evento {} contabilizado: lançamento {}", id, lanc.getId());
        } else {
            evento.setStatus("sem_regra");
            log.info("Evento {} sem roteiro — pendência para classificação", id);
        }

        eventoRepository.save(evento);
        return new EventoRecebidoResponse(id.toString(), evento.getStatus(), evento.getLancamentoId());
    }

    private UUID parseId(String eventoId) {
        try {
            return UUID.fromString(eventoId);
        } catch (Exception e) {
            throw new ModuloException("eventoId deve ser um UUID válido: " + eventoId);
        }
    }

    private String serializar(EventoContabilRequest req) {
        try {
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.warn("Falha ao serializar payload do evento: {}", e.getMessage());
            return null;
        }
    }
}
