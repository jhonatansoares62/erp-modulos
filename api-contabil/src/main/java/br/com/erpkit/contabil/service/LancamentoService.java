package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.Partida;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.model.RegraPartida;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.repository.RegraPartidaRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monta e posta o lançamento a partir de um roteiro. Impõe os invariantes do ledger:
 * só analítica recebe partida (F5), ≥1 débito e ≥1 crédito e sum(D)=sum(C) (F2), e o
 * lançamento nasce imutável ('lancado'). Correção só via estorno (wave posterior).
 */
@Service
public class LancamentoService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    private final RegraPartidaRepository regraPartidaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final PartidaRepository partidaRepository;
    private final ContaContabilService contaService;

    public LancamentoService(RegraPartidaRepository regraPartidaRepository,
                             LancamentoRepository lancamentoRepository,
                             PartidaRepository partidaRepository,
                             ContaContabilService contaService) {
        this.regraPartidaRepository = regraPartidaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.partidaRepository = partidaRepository;
        this.contaService = contaService;
    }

    @Transactional
    public Lancamento postarDeEvento(EventoContabilRequest evento, RegraLancamento regra) {
        List<RegraPartida> linhas = regraPartidaRepository.findByRegraIdOrderByOrdem(regra.getId());
        if (linhas.isEmpty()) {
            throw new ModuloException("Roteiro sem partidas configuradas: regra " + regra.getId());
        }

        List<Partida> partidas = new ArrayList<>();
        long totalDebito = 0;
        long totalCredito = 0;
        for (RegraPartida linha : linhas) {
            Long contaId = resolverConta(linha, evento.getContexto());
            contaService.validarRecebeLancamento(contaId);   // F5: só analítica
            long valor = calcularValor(linha, evento.getValorCentavos());
            if (valor <= 0) {
                throw new ModuloException("Partida com valor não positivo na regra " + regra.getId());
            }
            Partida p = new Partida();
            p.setContaId(contaId);
            p.setTipo(linha.getTipo());
            p.setValorCentavos(valor);
            p.setOrdem(linha.getOrdem());
            partidas.add(p);
            if ("D".equals(linha.getTipo())) totalDebito += valor; else totalCredito += valor;
        }

        // F2: pelo menos um débito e um crédito, e débitos = créditos.
        boolean temDebito = partidas.stream().anyMatch(p -> "D".equals(p.getTipo()));
        boolean temCredito = partidas.stream().anyMatch(p -> "C".equals(p.getTipo()));
        if (!temDebito || !temCredito) {
            throw new ModuloException("Lançamento precisa de ao menos um débito e um crédito");
        }
        if (totalDebito != totalCredito) {
            throw new ModuloException("Lançamento não balanceado: débitos=" + totalDebito
                    + " créditos=" + totalCredito + " (centavos)");
        }

        Lancamento lanc = new Lancamento();
        lanc.setNumero(lancamentoRepository.count() + 1);
        lanc.setDataCompetencia(evento.getDataEvento());
        lanc.setHistorico(renderHistorico(regra.getHistoricoTemplate(), evento));
        if (evento.getEventoId() != null) {
            lanc.setOrigemEventoId(java.util.UUID.fromString(evento.getEventoId()));
        }
        lanc.setOrigemDocumento(origemDocumento(evento));
        lanc.setStatus("lancado");
        lanc.setLancadoEm(Instant.now());
        Lancamento salvo = lancamentoRepository.save(lanc);

        for (Partida p : partidas) {
            p.setLancamentoId(salvo.getId());
            partidaRepository.save(p);
        }
        return salvo;
    }

    private Long resolverConta(RegraPartida linha, Map<String, Object> contexto) {
        if ("variavel".equals(linha.getContaModo())) {
            Long resolvida = resolverCaminho(linha.getContaCampo(), contexto);
            if (resolvida != null) {
                return resolvida;
            }
            // Fallback para a conta constante da própria linha, se houver.
            if (linha.getContaId() != null) {
                return linha.getContaId();
            }
            throw new ModuloException("Conta variável não resolvida (" + linha.getContaCampo()
                    + ") e sem conta constante de fallback");
        }
        if (linha.getContaId() == null) {
            throw new ModuloException("Regra constante sem conta definida (partida " + linha.getId() + ")");
        }
        return linha.getContaId();
    }

    private long calcularValor(RegraPartida linha, long valorTotal) {
        if ("percentual".equals(linha.getBase())) {
            BigDecimal pct = linha.getPercentual() == null ? BigDecimal.ZERO : linha.getPercentual();
            return BigDecimal.valueOf(valorTotal)
                    .multiply(pct)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
        return valorTotal;
    }

    @SuppressWarnings("unchecked")
    private Long resolverCaminho(String caminho, Map<String, Object> contexto) {
        if (caminho == null || contexto == null) return null;
        Object atual = contexto;
        for (String seg : caminho.split("\\.")) {
            if (!(atual instanceof Map<?, ?> m)) return null;
            atual = ((Map<String, Object>) m).get(seg);
            if (atual == null) return null;
        }
        if (atual instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(atual));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String renderHistorico(String template, EventoContabilRequest evento) {
        if (template == null || template.isBlank()) {
            return evento.getTipo();
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String path = m.group(1).trim();
            String valor = valorPlaceholder(path, evento);
            m.appendReplacement(sb, Matcher.quoteReplacement(valor));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String valorPlaceholder(String path, EventoContabilRequest evento) {
        if ("numero".equals(path) && evento.getReferencia() != null) {
            return nvl(evento.getReferencia().getNumero());
        }
        Long v = resolverCaminho(path, evento.getContexto());
        if (v != null) return String.valueOf(v);
        // tenta valor String direto do contexto (ex.: clienteRef.nome)
        Object obj = resolverObjeto(path, evento.getContexto());
        return obj == null ? "" : String.valueOf(obj);
    }

    @SuppressWarnings("unchecked")
    private Object resolverObjeto(String caminho, Map<String, Object> contexto) {
        if (caminho == null || contexto == null) return null;
        Object atual = contexto;
        for (String seg : caminho.split("\\.")) {
            if (!(atual instanceof Map<?, ?> m)) return null;
            atual = ((Map<String, Object>) m).get(seg);
            if (atual == null) return null;
        }
        return atual;
    }

    private String origemDocumento(EventoContabilRequest evento) {
        if (evento.getReferencia() == null) return null;
        String ent = nvl(evento.getReferencia().getEntidade());
        String num = nvl(evento.getReferencia().getNumero());
        String s = (ent + " " + num).trim();
        return s.isEmpty() ? null : s;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
