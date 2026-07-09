package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.LancamentoResponse;
import br.com.erpkit.contabil.dto.PartidaSpec;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.Partida;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.model.RegraPartida;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.repository.RegraPartidaRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final PeriodoService periodoService;

    public LancamentoService(RegraPartidaRepository regraPartidaRepository,
                             LancamentoRepository lancamentoRepository,
                             PartidaRepository partidaRepository,
                             ContaContabilService contaService,
                             PeriodoService periodoService) {
        this.regraPartidaRepository = regraPartidaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.partidaRepository = partidaRepository;
        this.contaService = contaService;
        this.periodoService = periodoService;
    }

    @Transactional
    public Lancamento postarDeEvento(EventoContabilRequest evento, RegraLancamento regra) {
        periodoService.validarPeriodoAberto(evento.getDataEvento());   // F8: lock date
        List<PartidaSpec> specs = montarSpecs(evento, regra);
        UUID origemEventoId = evento.getEventoId() == null ? null : UUID.fromString(evento.getEventoId());
        return postarInterno(evento.getDataEvento(), renderHistorico(regra.getHistoricoTemplate(), evento),
                specs, origemEventoId, origemDocumento(evento), "normal");
    }

    /** Resolve as partidas (conta + valor) de um evento por um roteiro, sem postar. */
    public List<PartidaSpec> montarSpecs(EventoContabilRequest evento, RegraLancamento regra) {
        List<RegraPartida> linhas = regraPartidaRepository.findByRegraIdOrderByOrdem(regra.getId());
        if (linhas.isEmpty()) {
            throw new ModuloException("Roteiro sem partidas configuradas: regra " + regra.getId());
        }
        List<PartidaSpec> specs = new ArrayList<>();
        for (RegraPartida linha : linhas) {
            Long contaId = resolverConta(linha, evento.getContexto());
            long valor = calcularValor(linha, evento.getValorCentavos());
            if (valor <= 0) {
                throw new ModuloException("Partida com valor não positivo na regra " + regra.getId());
            }
            specs.add(new PartidaSpec(contaId, linha.getTipo(), valor));
        }
        return specs;
    }

    /** True se as partidas do lançamento batem exatamente com as specs (conta, tipo, valor). */
    public boolean partidasConferem(Long lancamentoId, List<PartidaSpec> specs) {
        List<String> atuais = partidaRepository.findByLancamentoId(lancamentoId).stream()
                .map(p -> p.getContaId() + "|" + p.getTipo() + "|" + p.getValorCentavos()).sorted().toList();
        List<String> novas = specs.stream()
                .map(s -> s.getContaId() + "|" + s.getTipo() + "|" + s.getValorCentavos()).sorted().toList();
        return atuais.equals(novas);
    }

    public boolean isLancado(Long lancamentoId) {
        return lancamentoRepository.findById(lancamentoId)
                .map(l -> "lancado".equals(l.getStatus())).orElse(false);
    }

    /**
     * Posta um lançamento a partir de partidas explícitas (fora de roteiro) — usado pelo
     * encerramento de exercício. Impõe os mesmos invariantes de postarDeEvento (F5/F2), mas
     * NÃO chama validarPeriodoAberto: o encerramento cai em 31/12, mês que pode já estar
     * travado no fechamento mensal.
     */
    @Transactional
    public Lancamento postar(LocalDate dataCompetencia, String historico, List<PartidaSpec> partidas) {
        return postarInterno(dataCompetencia, historico, partidas, null, null, "normal");
    }

    /** Igual ao postar, com um documento de origem (marcador para localizar/reaplicar depois). */
    @Transactional
    public Lancamento postar(LocalDate dataCompetencia, String historico, List<PartidaSpec> partidas,
                             String origemDocumento) {
        return postarInterno(dataCompetencia, historico, partidas, null, origemDocumento, "normal");
    }

    /**
     * Posta um lançamento de ENCERRAMENTO de exercício (tipo='encerramento'). Igual ao postar,
     * mas marca o lançamento para a DRE poder excluí-lo (senão a DRE do ano zera após encerrar).
     */
    @Transactional
    public Lancamento postarEncerramento(LocalDate dataCompetencia, String historico, List<PartidaSpec> partidas) {
        return postarInterno(dataCompetencia, historico, partidas, null, null, "encerramento");
    }

    /** Núcleo compartilhado: valida (F5/F2) e persiste cabeçalho + partidas imutáveis ('lancado'). */
    private Lancamento postarInterno(LocalDate dataCompetencia, String historico, List<PartidaSpec> specs,
                                     UUID origemEventoId, String origemDocumento, String tipo) {
        List<Partida> partidas = new ArrayList<>();
        long totalDebito = 0;
        long totalCredito = 0;
        int ordem = 0;
        for (PartidaSpec spec : specs) {
            if (!"D".equals(spec.getTipo()) && !"C".equals(spec.getTipo())) {
                throw new ModuloException("Tipo de partida inválido (use D|C): " + spec.getTipo());
            }
            if (spec.getValorCentavos() <= 0) {
                throw new ModuloException("Partida com valor não positivo (centavos): " + spec.getValorCentavos());
            }
            contaService.validarRecebeLancamento(spec.getContaId());   // F5: só analítica
            Partida p = new Partida();
            p.setContaId(spec.getContaId());
            p.setTipo(spec.getTipo());
            p.setValorCentavos(spec.getValorCentavos());
            p.setOrdem(ordem++);
            partidas.add(p);
            if ("D".equals(spec.getTipo())) totalDebito += spec.getValorCentavos(); else totalCredito += spec.getValorCentavos();
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
        lanc.setDataCompetencia(dataCompetencia);
        lanc.setHistorico(historico);
        lanc.setOrigemEventoId(origemEventoId);
        lanc.setOrigemDocumento(origemDocumento);
        lanc.setStatus("lancado");
        lanc.setTipo(tipo);
        lanc.setLancadoEm(Instant.now());
        Lancamento salvo = lancamentoRepository.save(lanc);

        for (Partida p : partidas) {
            p.setLancamentoId(salvo.getId());
            partidaRepository.save(p);
        }
        return salvo;
    }

    /**
     * Estorna um lançamento postado: cria um novo lançamento com as partidas invertidas (D↔C),
     * ligado ao original (estornaId), e marca o original como 'estornado' (F3/F6, ITG 2000).
     * Nunca edita/apaga o original.
     */
    @Transactional
    public Lancamento estornar(Long lancamentoId, String motivo) {
        Lancamento original = lancamentoRepository.findById(lancamentoId)
                .orElseThrow(() -> new ModuloException("Lançamento não encontrado: " + lancamentoId, HttpStatus.NOT_FOUND));
        if (!"lancado".equals(original.getStatus())) {
            throw new ModuloException("Só é possível estornar lançamento 'lancado' (atual: " + original.getStatus() + ")");
        }
        periodoService.validarPeriodoAberto(original.getDataCompetencia());

        Lancamento estorno = new Lancamento();
        estorno.setNumero(lancamentoRepository.count() + 1);
        estorno.setDataCompetencia(original.getDataCompetencia());
        estorno.setHistorico("Estorno do lançamento " + original.getNumero()
                + (motivo == null || motivo.isBlank() ? "" : " - " + motivo));
        estorno.setEstornaId(original.getId());
        // A reversão herda o tipo do original: estorno de 'encerramento' também é 'encerramento' e
        // segue EXCLUÍDO da DRE e do recompute do encerramento (senão reabrir o exercício dobraria a
        // DRE). Estornos de 'normal' (devolução, reclassificação) continuam 'normal'.
        estorno.setTipo(original.getTipo());
        estorno.setStatus("lancado");
        estorno.setLancadoEm(Instant.now());
        Lancamento salvo = lancamentoRepository.save(estorno);

        for (Partida po : partidaRepository.findByLancamentoId(original.getId())) {
            Partida pe = new Partida();
            pe.setLancamentoId(salvo.getId());
            pe.setContaId(po.getContaId());
            pe.setTipo("D".equals(po.getTipo()) ? "C" : "D");   // inverte débito/crédito
            pe.setValorCentavos(po.getValorCentavos());
            pe.setOrdem(po.getOrdem());
            partidaRepository.save(pe);
        }

        original.setEstornadoPorId(salvo.getId());
        original.setStatus("estornado");
        lancamentoRepository.save(original);
        return salvo;
    }

    public LancamentoResponse detalhe(Long id) {
        Lancamento l = lancamentoRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Lançamento não encontrado: " + id, HttpStatus.NOT_FOUND));
        return toResponse(l);
    }

    public LancamentoResponse toResponse(Lancamento l) {
        return LancamentoResponse.de(l, partidaRepository.findByLancamentoId(l.getId()));
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
