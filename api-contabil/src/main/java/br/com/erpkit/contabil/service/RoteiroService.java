package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.repository.RegraLancamentoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Casa um evento com o roteiro (regra) aplicável. Semântica first-match por prioridade
 * (mais específica primeiro): entre as regras ativas e vigentes do tipo, retorna a de
 * maior prioridade cujas condições batem com o contexto do evento.
 */
@Service
public class RoteiroService {

    private static final Logger log = LoggerFactory.getLogger(RoteiroService.class);

    private final RegraLancamentoRepository regraRepository;
    private final ObjectMapper objectMapper;

    public RoteiroService(RegraLancamentoRepository regraRepository, ObjectMapper objectMapper) {
        this.regraRepository = regraRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<RegraLancamento> casar(String eventoTipo, Map<String, Object> contexto, LocalDate dataEvento) {
        var candidatas = regraRepository.findByEventoTipoAndAtivoTrueOrderByPrioridadeDesc(eventoTipo);
        for (RegraLancamento regra : candidatas) {
            if (vigente(regra, dataEvento) && condicoesBatem(regra.getCondicoes(), contexto)) {
                return Optional.of(regra);
            }
        }
        return Optional.empty();
    }

    private boolean vigente(RegraLancamento regra, LocalDate data) {
        if (data == null) return true;
        if (regra.getVigenciaInicio() != null && data.isBefore(regra.getVigenciaInicio())) return false;
        if (regra.getVigenciaFim() != null && data.isAfter(regra.getVigenciaFim())) return false;
        return true;
    }

    /** Regra sem condições casa sempre (genérica). Senão, todas as condições devem bater com o contexto. */
    private boolean condicoesBatem(String condicoesJson, Map<String, Object> contexto) {
        if (condicoesJson == null || condicoesJson.isBlank()) {
            return true;
        }
        Map<String, Object> condicoes;
        try {
            condicoes = objectMapper.readValue(condicoesJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Condições da regra com JSON inválido, ignorando regra: {}", e.getMessage());
            return false;
        }
        if (contexto == null) {
            return condicoes.isEmpty();
        }
        for (Map.Entry<String, Object> cond : condicoes.entrySet()) {
            Object valorContexto = contexto.get(cond.getKey());
            if (valorContexto == null || !String.valueOf(valorContexto).equals(String.valueOf(cond.getValue()))) {
                return false;
            }
        }
        return true;
    }
}
