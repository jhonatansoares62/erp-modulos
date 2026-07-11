package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ResumoUsoResponse;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Relatorio de uso do WhatsApp (V7 §12): agrega {@code mensagens_log} num periodo e
 * projeta um {@link ResumoUsoResponse} (sem PII). Padrao de agregacao espelha o
 * {@code EmailService.estatisticas()} do api-email, porem com cortes multiplos.
 *
 * <p>Estes relatorios sao LOCAIS (o que a Meta nao expoe por-paciente/por-mensagem);
 * o custo autoritativo por categoria vem do {@code pricing_analytics} da Meta (§11).
 */
@Service
public class RelatorioUsoService {

    private final MensagemLogRepository repository;

    public RelatorioUsoService(MensagemLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ResumoUsoResponse resumo(Instant de, Instant ate) {
        Map<String, Long> porDirecao = toMap(repository.contarPorDirecao(de, ate),
                k -> ((Direcao) k).name());
        long entrada = porDirecao.getOrDefault(Direcao.in.name(), 0L);
        long saida = porDirecao.getOrDefault(Direcao.out.name(), 0L);

        Map<String, Long> porTipo = toMap(repository.contarPorTipo(de, ate),
                k -> k == null ? "desconhecido" : (String) k);
        Map<String, Long> statusSaida = toMap(repository.contarPorStatus(Direcao.out, de, ate),
                k -> k == null ? "pendente" : (String) k);
        Map<String, Long> categoriaSaida = toMap(repository.contarPorCategoria(Direcao.out, de, ate),
                k -> k == null ? "sem_categoria" : (String) k);
        long faturaveis = repository.contarFaturaveis(Direcao.out, de, ate);
        Map<String, Long> porResultado = toMap(repository.contarPorResultado(Direcao.in, de, ate),
                k -> k == null ? "pendente" : (String) k);

        return new ResumoUsoResponse(de, ate, entrada + saida, entrada, saida,
                faturaveis, porTipo, statusSaida, categoriaSaida, porResultado);
    }

    /** Converte {@code List<Object[]>{chave, count}} em {@code Map<rotulo, Long>} preservando ordem. */
    private static Map<String, Long> toMap(List<Object[]> linhas, Function<Object, String> rotulo) {
        Map<String, Long> mapa = new LinkedHashMap<>();
        for (Object[] linha : linhas) {
            mapa.put(rotulo.apply(linha[0]), (Long) linha[1]);
        }
        return mapa;
    }
}
