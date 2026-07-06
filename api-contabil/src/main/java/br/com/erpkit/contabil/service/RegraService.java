package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.RegraCreateDTO;
import br.com.erpkit.contabil.dto.RegraPartidaDTO;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.model.RegraPartida;
import br.com.erpkit.contabil.repository.RegraLancamentoRepository;
import br.com.erpkit.contabil.repository.RegraPartidaRepository;
import br.com.erpkit.shared.exception.ModuloException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cadastro de roteiros (regras). Valida a forma da regra (≥1 débito e ≥1 crédito), resolve
 * contas por código no modo constante e persiste regra + partidas.
 */
@Service
public class RegraService {

    private final RegraLancamentoRepository regraRepository;
    private final RegraPartidaRepository partidaRepository;
    private final ContaContabilService contaService;
    private final ObjectMapper objectMapper;

    public RegraService(RegraLancamentoRepository regraRepository,
                        RegraPartidaRepository partidaRepository,
                        ContaContabilService contaService,
                        ObjectMapper objectMapper) {
        this.regraRepository = regraRepository;
        this.partidaRepository = partidaRepository;
        this.contaService = contaService;
        this.objectMapper = objectMapper;
    }

    public List<RegraLancamento> listarPorEvento(String eventoTipo) {
        return regraRepository.findByEventoTipoAndAtivoTrueOrderByPrioridadeDesc(eventoTipo);
    }

    @Transactional
    public RegraLancamento criar(RegraCreateDTO dto) {
        if (dto.getEventoTipo() == null || dto.getEventoTipo().isBlank()) {
            throw new ModuloException("Tipo de evento é obrigatório");
        }
        List<RegraPartidaDTO> partidas = dto.getPartidas();
        if (partidas == null || partidas.size() < 2) {
            throw new ModuloException("A regra precisa de ao menos duas partidas");
        }
        boolean temDebito = partidas.stream().anyMatch(p -> "D".equals(p.getTipo()));
        boolean temCredito = partidas.stream().anyMatch(p -> "C".equals(p.getTipo()));
        if (!temDebito || !temCredito) {
            throw new ModuloException("A regra precisa de ao menos um débito e um crédito");
        }

        RegraLancamento regra = new RegraLancamento();
        regra.setEventoTipo(dto.getEventoTipo().trim());
        regra.setPrioridade(dto.getPrioridade());
        regra.setCondicoes(serializarCondicoes(dto.getCondicoes()));
        regra.setHistoricoTemplate(dto.getHistoricoTemplate());
        regra.setVigenciaInicio(dto.getVigenciaInicio());
        regra.setVigenciaFim(dto.getVigenciaFim());
        regra.setVersao(1);
        regra.setAtivo(true);
        RegraLancamento salva = regraRepository.save(regra);

        int ordem = 0;
        for (RegraPartidaDTO p : partidas) {
            RegraPartida rp = new RegraPartida();
            rp.setRegraId(salva.getId());
            rp.setTipo(p.getTipo());
            rp.setOrdem(ordem++);
            rp.setContaModo(p.getContaModo() == null ? "constante" : p.getContaModo());
            rp.setContaCampo(p.getContaCampo());
            rp.setBase(p.getBase() == null ? "valor_total" : p.getBase());
            rp.setPercentual(p.getPercentual());
            if ("variavel".equals(rp.getContaModo())) {
                if (p.getContaCodigo() != null && !p.getContaCodigo().isBlank()) {
                    rp.setContaId(contaService.buscarPorCodigo(p.getContaCodigo()).getId());   // fallback constante
                }
            } else {
                if (p.getContaCodigo() == null || p.getContaCodigo().isBlank()) {
                    throw new ModuloException("Partida constante sem conta (código)");
                }
                rp.setContaId(contaService.buscarPorCodigo(p.getContaCodigo()).getId());
            }
            partidaRepository.save(rp);
        }
        return salva;
    }

    private String serializarCondicoes(java.util.Map<String, Object> condicoes) {
        if (condicoes == null || condicoes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(condicoes);
        } catch (Exception e) {
            throw new ModuloException("Condições inválidas: " + e.getMessage());
        }
    }
}
