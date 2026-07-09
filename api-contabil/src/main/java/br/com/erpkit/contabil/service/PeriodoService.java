package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.EncerramentoPreviewResponse;
import br.com.erpkit.contabil.dto.EncerramentoResponse;
import br.com.erpkit.contabil.dto.PartidaSpec;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.model.PeriodoFechado;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.repository.PeriodoFechadoRepository;
import br.com.erpkit.shared.exception.ModuloException;
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

    private final PeriodoFechadoRepository repository;
    private final PartidaRepository partidaRepository;
    private final ContaContabilRepository contaRepository;
    private final ContaContabilService contaService;
    private final LancamentoService lancamentoService;

    // @Lazy no lancamentoService: LancamentoService injeta PeriodoService (validarPeriodoAberto);
    // o encerramento inverte a dependência (posta lançamentos), fechando um ciclo que o Lazy quebra.
    public PeriodoService(PeriodoFechadoRepository repository,
                          PartidaRepository partidaRepository,
                          ContaContabilRepository contaRepository,
                          ContaContabilService contaService,
                          @Lazy LancamentoService lancamentoService) {
        this.repository = repository;
        this.partidaRepository = partidaRepository;
        this.contaRepository = contaRepository;
        this.contaService = contaService;
        this.lancamentoService = lancamentoService;
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

    /** Rejeita lançamento em período fechado: mês da competência <= último mês fechado. */
    public void validarPeriodoAberto(LocalDate dataCompetencia) {
        repository.findFirstByTipoOrderByCompetenciaDesc("mensal").ifPresent(ultimo -> {
            String mesLanc = dataCompetencia.format(YM);
            if (mesLanc.compareTo(ultimo.getCompetencia()) <= 0) {
                throw new ModuloException("Período " + ultimo.getCompetencia()
                        + " está fechado; não é possível lançar em " + mesLanc);
            }
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

        // (a) Encerramento das receitas: D cada receita (saldo credor) · C ARE (soma).
        List<PartidaSpec> partidasReceita = new ArrayList<>();
        long totalReceitas = 0;
        for (ContaContabil c : contasResultado) {
            if (!"receita".equals(c.getGrupo())) continue;
            long[] dc = movimento.get(c.getId());
            if (dc == null) continue;
            long valor = dc[1] - dc[0];   // crédito - débito (saldo credor)
            if (valor > 0) {
                partidasReceita.add(new PartidaSpec(c.getId(), "D", valor));
                totalReceitas += valor;
            }
        }
        if (totalReceitas > 0) {
            partidasReceita.add(new PartidaSpec(areId, "C", totalReceitas));
            lancamentoIds.add(lancamentoService.postarEncerramento(dataEnc,
                    "Encerramento das receitas do exercicio " + ano, partidasReceita).getId());
        }

        // (b) Encerramento de custos e despesas: C cada conta (saldo devedor) · D ARE (soma).
        List<PartidaSpec> partidasDespesa = new ArrayList<>();
        long totalDespesas = 0;
        for (ContaContabil c : contasResultado) {
            if (!"custo".equals(c.getGrupo()) && !"despesa".equals(c.getGrupo())) continue;
            long[] dc = movimento.get(c.getId());
            if (dc == null) continue;
            long valor = dc[0] - dc[1];   // débito - crédito (saldo devedor)
            if (valor > 0) {
                partidasDespesa.add(new PartidaSpec(c.getId(), "C", valor));
                totalDespesas += valor;
            }
        }
        if (totalDespesas > 0) {
            partidasDespesa.add(new PartidaSpec(areId, "D", totalDespesas));
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
                case "receita" -> receitas += Math.max(0, credito - debito);   // saldo credor
                case "custo" -> custos += Math.max(0, debito - credito);        // saldo devedor
                case "despesa" -> despesas += Math.max(0, debito - credito);    // saldo devedor
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
