package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.EncerramentoPreviewResponse;
import br.com.erpkit.contabil.dto.EncerramentoResponse;
import br.com.erpkit.contabil.dto.PartidaSpec;
import br.com.erpkit.contabil.dto.ReaberturaResponse;
import br.com.erpkit.contabil.dto.ReprocessamentoResponse;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.PeriodoFechado;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.repository.PeriodoFechadoRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Fechamento de período (lock date). Um lançamento cujo mês de competência é anterior ou igual
 * ao último período mensal fechado é rejeitado (F8). Fechar não zera nada (só trava); o
 * encerramento de exercício (que zera resultado) é wave posterior.
 */
@Service
public class PeriodoService {

    private static final Pattern MENSAL = Pattern.compile("\\d{4}-\\d{2}");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Logger log = LoggerFactory.getLogger(PeriodoService.class);

    private final PeriodoFechadoRepository repository;
    private final PartidaRepository partidaRepository;
    private final ContaContabilRepository contaRepository;
    private final ContaContabilService contaService;
    private final LancamentoService lancamentoService;
    private final LancamentoRepository lancamentoRepository;
    private final PendenciaService pendenciaService;

    // @Lazy no lancamentoService e pendenciaService: ambos injetam PeriodoService (validarPeriodoAberto/
    // motivoPeriodoFechado); o encerramento e a reabertura invertem a dependência (postam lançamentos,
    // reprocessam pendências), fechando ciclos que o Lazy quebra.
    public PeriodoService(PeriodoFechadoRepository repository,
                          PartidaRepository partidaRepository,
                          ContaContabilRepository contaRepository,
                          ContaContabilService contaService,
                          @Lazy LancamentoService lancamentoService,
                          LancamentoRepository lancamentoRepository,
                          @Lazy PendenciaService pendenciaService) {
        this.repository = repository;
        this.partidaRepository = partidaRepository;
        this.contaRepository = contaRepository;
        this.contaService = contaService;
        this.lancamentoService = lancamentoService;
        this.lancamentoRepository = lancamentoRepository;
        this.pendenciaService = pendenciaService;
    }

    @Transactional
    public PeriodoFechado fecharMensal(String competencia, String fechadoPor) {
        if (competencia == null || !MENSAL.matcher(competencia).matches()) {
            throw new ModuloException("Competência mensal inválida (use YYYY-MM): " + competencia);
        }
        if (repository.existsByCompetenciaAndTipo(competencia, "mensal")) {
            throw new ModuloException("Período " + competencia + " já está fechado", HttpStatus.CONFLICT);
        }
        PeriodoFechado p = new PeriodoFechado();
        p.setCompetencia(competencia);
        p.setTipo("mensal");
        p.setFechadoPor(fechadoPor);
        return repository.save(p);
    }

    public List<PeriodoFechado> listar() {
        return repository.findAllByOrderByCompetenciaDesc();
    }

    /**
     * Motivo de o período estar fechado para esta competência, se estiver — sem lançar.
     * Fonte única das DUAS travas (mês fechado e exercício encerrado): o caminho que estoura
     * ({@link #validarPeriodoAberto}) e o caminho que pendencia (ingestão de evento do ERP)
     * consultam o MESMO check, para nunca divergirem. {@code Optional.empty()} = período aberto.
     */
    public Optional<String> motivoPeriodoFechado(LocalDate dataCompetencia) {
        String mesLanc = dataCompetencia.format(YM);
        // Trava mensal: mês da competência <= último mês fechado.
        Optional<PeriodoFechado> mensal = repository.findFirstByTipoOrderByCompetenciaDesc("mensal");
        if (mensal.isPresent() && mesLanc.compareTo(mensal.get().getCompetencia()) <= 0) {
            return Optional.of("Período " + mensal.get().getCompetencia()
                    + " está fechado; não é possível lançar em " + mesLanc);
        }
        // Trava de exercício encerrado: ano da competência <= maior exercício encerrado. Necessária
        // porque encerrarExercicio grava periodo_fechado tipo='exercicio' SEM criar fechamentos
        // mensais — sem esta trava, um evento datado no ano encerrado passaria pela trava mensal.
        Optional<PeriodoFechado> exercicio = repository.findFirstByTipoOrderByCompetenciaDesc("exercicio");
        if (exercicio.isPresent()) {
            int anoEnc = Integer.parseInt(exercicio.get().getCompetencia());
            if (dataCompetencia.getYear() <= anoEnc) {
                return Optional.of("Exercício " + anoEnc
                        + " está encerrado; não é possível lançar em " + mesLanc);
            }
        }
        return Optional.empty();
    }

    /** Rejeita lançamento em período fechado (mês fechado OU exercício encerrado). F8: lock date. */
    public void validarPeriodoAberto(LocalDate dataCompetencia) {
        motivoPeriodoFechado(dataCompetencia).ifPresent(msg -> {
            throw new ModuloException(msg);
        });
    }

    /**
     * Encerramento de exercício (ITG 1000 / CFC §6): zera as contas de resultado
     * (receitas/custos/despesas) contra a conta transitória ARE (3.9.9.01) e transfere o
     * resultado para o PL (Lucros 2.3.3.01 ou Prejuízos 2.3.3.02). Idempotência dura: um
     * exercício só encerra uma vez (periodo_fechado tipo 'exercicio').
     */
    @Transactional
    public EncerramentoResponse encerrarExercicio(int ano, String por) {
        LocalDate de = LocalDate.of(ano, 1, 1);
        LocalDate ate = LocalDate.of(ano, 12, 31);
        LocalDate dataEnc = ate;
        String competencia = String.valueOf(ano);

        if (repository.existsByCompetenciaAndTipo(competencia, "exercicio")) {
            throw new ModuloException("Exercício " + ano + " já está encerrado", HttpStatus.CONFLICT);
        }

        // Snapshot dos saldos ANTES de postar o encerramento: contaId -> [debito, credito].
        // Exclui encerramentos anteriores (idempotência já bloqueia re-encerrar, mas mantém o
        // snapshot idêntico ao do preview, que também os exclui).
        Map<Long, long[]> movimento = new HashMap<>();
        for (Object[] row : partidaRepository.somarPorContaExcluindoEncerramento(de, ate)) {
            movimento.put(num(row[0]), new long[]{num(row[1]), num(row[2])});
        }

        List<ContaContabil> contasResultado = contaRepository.findByGrupoInAndAtivoTrue(
                List.of("receita", "custo", "despesa"));
        Long areId = contaService.buscarPorCodigo("3.9.9.01").getId();
        Long lucrosId = contaService.buscarPorCodigo("2.3.3.01").getId();
        Long prejuizosId = contaService.buscarPorCodigo("2.3.3.02").getId();

        List<Long> lancamentoIds = new ArrayList<>();

        // (a) Encerramento das receitas: zera CADA conta do grupo receita pelo seu saldo (inclusive
        // retificadoras/contra-receita, como deduções e devoluções — saldo devedor), e joga o
        // líquido na ARE. Assim nenhuma conta de receita fica aberta e o líquido = receita líquida.
        List<PartidaSpec> partidasReceita = new ArrayList<>();
        long totalReceitas = 0;   // receita líquida do exercício (Σ crédito − débito do grupo)
        for (ContaContabil c : contasResultado) {
            if (!"receita".equals(c.getGrupo())) continue;
            long[] dc = movimento.get(c.getId());
            if (dc == null) continue;
            long saldoCredor = dc[1] - dc[0];   // crédito - débito
            if (saldoCredor > 0) partidasReceita.add(new PartidaSpec(c.getId(), "D", saldoCredor));
            else if (saldoCredor < 0) partidasReceita.add(new PartidaSpec(c.getId(), "C", -saldoCredor));
            totalReceitas += saldoCredor;
        }
        if (!partidasReceita.isEmpty() && totalReceitas != 0) {
            partidasReceita.add(new PartidaSpec(areId, totalReceitas > 0 ? "C" : "D", Math.abs(totalReceitas)));
            lancamentoIds.add(lancamentoService.postarEncerramento(dataEnc,
                    "Encerramento das receitas do exercicio " + ano, partidasReceita).getId());
        }

        // (b) Encerramento de custos e despesas: zera cada conta pelo saldo devedor (e contra-despesas
        // pelo credor), líquido → ARE.
        List<PartidaSpec> partidasDespesa = new ArrayList<>();
        long totalDespesas = 0;   // custos + despesas do exercício (Σ débito − crédito do grupo)
        for (ContaContabil c : contasResultado) {
            if (!"custo".equals(c.getGrupo()) && !"despesa".equals(c.getGrupo())) continue;
            long[] dc = movimento.get(c.getId());
            if (dc == null) continue;
            long saldoDevedor = dc[0] - dc[1];   // débito - crédito
            if (saldoDevedor > 0) partidasDespesa.add(new PartidaSpec(c.getId(), "C", saldoDevedor));
            else if (saldoDevedor < 0) partidasDespesa.add(new PartidaSpec(c.getId(), "D", -saldoDevedor));
            totalDespesas += saldoDevedor;
        }
        if (!partidasDespesa.isEmpty() && totalDespesas != 0) {
            partidasDespesa.add(new PartidaSpec(areId, totalDespesas > 0 ? "D" : "C", Math.abs(totalDespesas)));
            lancamentoIds.add(lancamentoService.postarEncerramento(dataEnc,
                    "Encerramento de custos e despesas do exercicio " + ano, partidasDespesa).getId());
        }

        // (c) Apuração: transfere o resultado da ARE para o PL.
        long resultado = totalReceitas - totalDespesas;
        if (resultado > 0) {
            List<PartidaSpec> apuracao = List.of(
                    new PartidaSpec(areId, "D", resultado),
                    new PartidaSpec(lucrosId, "C", resultado));
            lancamentoIds.add(lancamentoService.postarEncerramento(dataEnc,
                    "Apuracao do resultado do exercicio " + ano, apuracao).getId());
        } else if (resultado < 0) {
            long prejuizo = -resultado;
            List<PartidaSpec> apuracao = List.of(
                    new PartidaSpec(prejuizosId, "D", prejuizo),
                    new PartidaSpec(areId, "C", prejuizo));
            lancamentoIds.add(lancamentoService.postarEncerramento(dataEnc,
                    "Apuracao do resultado do exercicio " + ano, apuracao).getId());
        }

        PeriodoFechado pf = new PeriodoFechado();
        pf.setCompetencia(competencia);
        pf.setTipo("exercicio");
        pf.setFechadoPor(por);
        repository.save(pf);

        return new EncerramentoResponse(ano, totalReceitas, totalDespesas, resultado, lancamentoIds);
    }

    /**
     * Reabre um exercício encerrado (correção contábil). Ordem importa: (1) remove a trava do exercício
     * — sem isso os estornos, que validam período, seriam rejeitados; (2) reverte os lançamentos de
     * encerramento do ano (a reversão herda tipo='encerramento', então a DRE volta a = movimento e o
     * transporte para o PL é desfeito); (3) reprocessa as pendências 'periodo_fechado' do ano, que
     * agora postam. Só reverte ENCERRAMENTOS ORIGINAIS (estornaId nulo) — nunca reversões anteriores,
     * pra reabrir múltiplas vezes não dobrar. O contador deve RE-ENCERRAR depois.
     */
    @Transactional
    public ReaberturaResponse reabrirExercicio(int ano, String por, String motivo) {
        String competencia = String.valueOf(ano);
        PeriodoFechado exercicio = repository.findByCompetenciaAndTipo(competencia, "exercicio")
                .orElseThrow(() -> new ModuloException("Exercício " + ano + " não está encerrado", HttpStatus.CONFLICT));
        LocalDate de = LocalDate.of(ano, 1, 1);
        LocalDate ate = LocalDate.of(ano, 12, 31);

        // 1. Remove a trava e faz o flush ANTES dos estornos (que chamam validarPeriodoAberto).
        repository.delete(exercicio);
        repository.flush();

        // 2. Reverte os lançamentos de encerramento ORIGINAIS do ano (pula reversões: estornaId != null).
        int estornados = 0;
        for (Lancamento l : lancamentoRepository.findByTipoAndStatusAndDataCompetenciaBetween(
                "encerramento", "lancado", de, ate)) {
            if (l.getEstornaId() != null) continue;
            lancamentoService.estornar(l.getId(), "Reabertura do exercício " + ano);
            estornados++;
        }

        // 3. Reprocessa as pendências de período fechado do ano (agora que o ano está aberto).
        ReprocessamentoResponse rep = pendenciaService.reprocessarPeriodoFechado(ano);

        log.info("Exercício {} reaberto por '{}' (motivo: {}): {} lançamentos de encerramento estornados, "
                + "{} pendências reprocessadas", ano, por, motivo, estornados, rep.getReprocessados());
        return new ReaberturaResponse(ano, estornados, rep.getReprocessados());
    }

    /**
     * Prévia do encerramento (sem postar): apura receitas, custos, despesas e resultado do ano
     * com a MESMA soma do encerramento (excluindo encerramentos anteriores), para o contador
     * conferir antes de confirmar. Também informa se o exercício já está encerrado.
     */
    public EncerramentoPreviewResponse previewEncerramento(int ano) {
        LocalDate de = LocalDate.of(ano, 1, 1);
        LocalDate ate = LocalDate.of(ano, 12, 31);
        boolean encerrado = repository.existsByCompetenciaAndTipo(String.valueOf(ano), "exercicio");

        Map<Long, String> grupoPorConta = new HashMap<>();
        for (ContaContabil c : contaRepository.findAll()) {
            grupoPorConta.put(c.getId(), c.getGrupo());
        }

        long receitas = 0, custos = 0, despesas = 0;
        for (Object[] row : partidaRepository.somarPorContaExcluindoEncerramento(de, ate)) {
            String grupo = grupoPorConta.get(num(row[0]));
            if (grupo == null) continue;
            long debito = num(row[1]);
            long credito = num(row[2]);
            switch (grupo) {
                // receita líquida: soma o grupo receita já líquido de retificadoras (deduções,
                // devoluções têm saldo devedor e entram negativas) — igual ao que o encerramento posta.
                case "receita" -> receitas += (credito - debito);
                case "custo" -> custos += (debito - credito);
                case "despesa" -> despesas += (debito - credito);
                default -> { /* contas patrimoniais não entram na apuração */ }
            }
        }
        long resultado = receitas - custos - despesas;
        return new EncerramentoPreviewResponse(ano, encerrado, receitas, custos, despesas, resultado);
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }
}
