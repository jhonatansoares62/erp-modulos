package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.dto.PendenciaResponse;
import br.com.erpkit.contabil.dto.RegraCreateDTO;
import br.com.erpkit.contabil.dto.ReprocessamentoResponse;
import br.com.erpkit.contabil.model.EventoRecebido;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.repository.EventoRecebidoRepository;
import br.com.erpkit.shared.exception.ModuloException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fila de pendências: eventos que chegaram sem roteiro (status sem_regra). O contador
 * classifica criando um roteiro; o evento guardado é então reprocessado (killer feature:
 * classifica uma vez, automatiza para sempre). O payload bruto guardado permite reprocessar
 * sem pedir nada ao ERP.
 */
@Service
public class PendenciaService {

    private final EventoRecebidoRepository eventoRepository;
    private final RegraService regraService;
    private final RoteiroService roteiroService;
    private final LancamentoService lancamentoService;
    private final PeriodoService periodoService;
    private final ObjectMapper objectMapper;

    public PendenciaService(EventoRecebidoRepository eventoRepository,
                            RegraService regraService,
                            RoteiroService roteiroService,
                            LancamentoService lancamentoService,
                            PeriodoService periodoService,
                            ObjectMapper objectMapper) {
        this.eventoRepository = eventoRepository;
        this.regraService = regraService;
        this.roteiroService = roteiroService;
        this.lancamentoService = lancamentoService;
        this.periodoService = periodoService;
        this.objectMapper = objectMapper;
    }

    /** Fila de pendências: sem roteiro (sem_regra) E presas em período fechado (periodo_fechado). */
    public List<PendenciaResponse> listar() {
        return eventoRepository.findByStatusInOrderByRecebidoEm(List.of("sem_regra", "periodo_fechado"))
                .stream().map(PendenciaResponse::de).toList();
    }

    @Transactional
    public EventoRecebidoResponse salvarComoRegra(String eventoId, RegraCreateDTO dto) {
        EventoRecebido evento = eventoRepository.findById(parseId(eventoId))
                .orElseThrow(() -> new ModuloException("Evento não encontrado: " + eventoId, HttpStatus.NOT_FOUND));
        if (!"sem_regra".equals(evento.getStatus())) {
            throw new ModuloException("Evento não está pendente (status: " + evento.getStatus() + ")");
        }

        // A regra é sempre para o tipo do evento pendente.
        dto.setEventoTipo(evento.getTipo());
        regraService.criar(dto);

        // Reprocessa o evento guardado com a nova regra.
        EventoContabilRequest req = desserializar(evento.getPayload());
        Optional<RegraLancamento> regra = roteiroService.casar(evento.getTipo(), req.getContexto(), evento.getDataEvento());
        if (regra.isEmpty()) {
            // Regra criada mas as condições não casaram com este evento — segue pendente.
            return new EventoRecebidoResponse(eventoId, evento.getStatus(), null);
        }
        Lancamento lanc = lancamentoService.postarDeEvento(req, regra.get());
        evento.setStatus("processado");
        evento.setLancamentoId(lanc.getId());
        evento.setProcessadoEm(Instant.now());
        eventoRepository.save(evento);
        return new EventoRecebidoResponse(eventoId, "processado", lanc.getId());
    }

    /**
     * Reprocessa em lote toda a fila de pendências: re-passa cada evento sem_regra pelo motor
     * de roteiros. Os que agora casam com alguma regra viram lançamento (útil depois de o
     * contador cadastrar novas regras). Os que não casam seguem pendentes.
     */
    /**
     * Reprocessa em lote TODA a fila (botão da tela): sem_regra (re-casa roteiro) E periodo_fechado
     * (re-checa período). Os que agora casam e cujo período está aberto viram lançamento; os demais
     * seguem pendentes. Idempotente: só posta o que dá.
     */
    @Transactional
    public ReprocessamentoResponse reprocessar() {
        return reprocessarLote(eventoRepository.findByStatusInOrderByRecebidoEm(List.of("sem_regra", "periodo_fechado")));
    }

    /**
     * Reprocessa as pendências 'periodo_fechado' (opcionalmente de um ano). Usado pela reabertura de
     * exercício: depois de destravar o ano, os eventos presos postam. Idempotente.
     */
    @Transactional
    public ReprocessamentoResponse reprocessarPeriodoFechado(Integer ano) {
        List<EventoRecebido> pendentes = eventoRepository.findByStatusOrderByRecebidoEm("periodo_fechado").stream()
                .filter(e -> ano == null || (e.getDataEvento() != null && e.getDataEvento().getYear() == ano))
                .toList();
        return reprocessarLote(pendentes);
    }

    private ReprocessamentoResponse reprocessarLote(List<EventoRecebido> pendentes) {
        int reprocessados = 0;
        for (EventoRecebido evento : pendentes) {
            if (tentarContabilizar(evento)) reprocessados++;
        }
        return new ReprocessamentoResponse(pendentes.size(), reprocessados, pendentes.size() - reprocessados);
    }

    /**
     * Tenta contabilizar um evento pendente sem estourar (seguro em lote): não casa roteiro → sem_regra;
     * casa mas período fechado → periodo_fechado (com motivo); casa e período aberto → posta e vira
     * processado. Retorna true só quando de fato virou lançamento.
     */
    private boolean tentarContabilizar(EventoRecebido evento) {
        EventoContabilRequest req = desserializar(evento.getPayload());
        Optional<RegraLancamento> regra = roteiroService.casar(evento.getTipo(), req.getContexto(), evento.getDataEvento());
        if (regra.isEmpty()) {
            if (!"sem_regra".equals(evento.getStatus())) {
                evento.setStatus("sem_regra");
                eventoRepository.save(evento);
            }
            return false;
        }
        Optional<String> motivoFechado = periodoService.motivoPeriodoFechado(req.getDataEvento());
        if (motivoFechado.isPresent()) {
            evento.setStatus("periodo_fechado");
            evento.setErroMensagem(motivoFechado.get());
            eventoRepository.save(evento);
            return false;
        }
        Lancamento lanc = lancamentoService.postarDeEvento(req, regra.get());
        evento.setStatus("processado");
        evento.setLancamentoId(lanc.getId());
        evento.setProcessadoEm(Instant.now());
        eventoRepository.save(evento);
        return true;
    }

    private UUID parseId(String eventoId) {
        try {
            return UUID.fromString(eventoId);
        } catch (Exception e) {
            throw new ModuloException("eventoId inválido: " + eventoId);
        }
    }

    private EventoContabilRequest desserializar(String payload) {
        if (payload == null) {
            throw new ModuloException("Evento sem payload guardado — não é possível reprocessar");
        }
        try {
            return objectMapper.readValue(payload, EventoContabilRequest.class);
        } catch (Exception e) {
            throw new ModuloException("Falha ao ler o payload do evento: " + e.getMessage());
        }
    }
}
