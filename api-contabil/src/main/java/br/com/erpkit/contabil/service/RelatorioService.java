package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.BalancoResponse;
import br.com.erpkit.contabil.dto.BalanceteResponse;
import br.com.erpkit.contabil.dto.DiarioResponse;
import br.com.erpkit.contabil.dto.DreResponse;
import br.com.erpkit.contabil.dto.RazaoResponse;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Relatórios contábeis derivados das partidas (F1: saldo é derivado, não armazenado).
 * Balancete e DRE a partir da soma D/C por conta no período; razão por conta.
 */
@Service
public class RelatorioService {

    private final PartidaRepository partidaRepository;
    private final ContaContabilRepository contaRepository;
    private final ContaContabilService contaService;

    public RelatorioService(PartidaRepository partidaRepository,
                            ContaContabilRepository contaRepository,
                            ContaContabilService contaService) {
        this.partidaRepository = partidaRepository;
        this.contaRepository = contaRepository;
        this.contaService = contaService;
    }

    public BalanceteResponse balancete(LocalDate de, LocalDate ate) {
        Map<Long, ContaContabil> contas = contaRepository.findAll().stream()
                .collect(Collectors.toMap(ContaContabil::getId, c -> c));
        List<BalanceteResponse.Linha> linhas = new ArrayList<>();
        long totalDebitos = 0;
        long totalCreditos = 0;
        for (Object[] row : partidaRepository.somarPorConta(de, ate)) {
            long contaId = num(row[0]);
            long debito = num(row[1]);
            long credito = num(row[2]);
            ContaContabil conta = contas.get(contaId);
            if (conta == null) continue;
            long saldo = debito - credito;
            String saldoNatureza = saldo >= 0 ? "D" : "C";
            linhas.add(new BalanceteResponse.Linha(
                    conta.getCodigo(), conta.getNome(), debito, credito, Math.abs(saldo), saldoNatureza));
            totalDebitos += debito;
            totalCreditos += credito;
        }
        linhas.sort(Comparator.comparing(BalanceteResponse.Linha::getCodigo));
        return new BalanceteResponse(de, ate, linhas, totalDebitos, totalCreditos);
    }

    public DreResponse dre(LocalDate de, LocalDate ate) {
        Map<Long, ContaContabil> contas = contaRepository.findAll().stream()
                .collect(Collectors.toMap(ContaContabil::getId, c -> c));
        long receitaBruta = 0, deducoes = 0, devolucoes = 0, custos = 0, despOper = 0, despFin = 0, recFin = 0;
        // Exclui os lançamentos de encerramento: senão a DRE do ano zera depois de encerrar o exercício.
        for (Object[] row : partidaRepository.somarPorContaExcluindoEncerramento(de, ate)) {
            ContaContabil conta = contas.get(num(row[0]));
            if (conta == null) continue;
            long debito = num(row[1]);
            long credito = num(row[2]);
            switch (conta.getGrupo()) {
                case "receita" -> {
                    if (conta.getCodigo().startsWith("3.1.2")) recFin += (credito - debito);          // receita financeira
                    else if (conta.getCodigo().startsWith("3.1.1.05")) devolucoes += (debito - credito); // devoluções/cancelamentos
                    else if (conta.isRetificadora()) deducoes += (debito - credito);                    // deduções de tributos
                    else receitaBruta += (credito - debito);
                }
                case "custo" -> custos += (debito - credito);
                case "despesa" -> {
                    if (conta.getCodigo().startsWith("3.2.3")) despFin += (debito - credito);
                    else despOper += (debito - credito);
                }
                default -> { /* contas patrimoniais não entram na DRE */ }
            }
        }
        DreResponse dre = new DreResponse();
        dre.setDe(de);
        dre.setAte(ate);
        dre.setReceitaBruta(receitaBruta);
        dre.setDeducoes(deducoes);
        dre.setDevolucoes(devolucoes);
        long receitaLiquida = receitaBruta - deducoes - devolucoes;
        dre.setReceitaLiquida(receitaLiquida);
        dre.setCustos(custos);
        long lucroBruto = receitaLiquida - custos;
        dre.setLucroBruto(lucroBruto);
        dre.setDespesasOperacionais(despOper);
        dre.setDespesasFinanceiras(despFin);
        dre.setReceitasFinanceiras(recFin);
        dre.setResultadoLiquido(lucroBruto - despOper - despFin + recFin);
        return dre;
    }

    /**
     * Balanço Patrimonial na data (posição acumulada do início até 'data'). ATIVO = saldo devedor
     * (D-C); PASSIVO+PL = saldo credor (C-D). O resultado do exercício ainda não encerrado
     * (receitas - custos - despesas) entra no PL como linha sintética para o balanço fechar.
     */
    public BalancoResponse balanco(LocalDate data) {
        Map<Long, ContaContabil> contas = contaRepository.findAll().stream()
                .collect(Collectors.toMap(ContaContabil::getId, c -> c));
        List<BalancoResponse.Linha> ativo = new ArrayList<>();
        List<BalancoResponse.Linha> passivoPl = new ArrayList<>();
        long totalAtivo = 0;
        long totalPassivoPl = 0;
        long resultado = 0;
        for (Object[] row : partidaRepository.somarPorConta(LocalDate.of(1900, 1, 1), data)) {
            ContaContabil conta = contas.get(num(row[0]));
            if (conta == null) continue;
            long debito = num(row[1]);
            long credito = num(row[2]);
            switch (conta.getGrupo()) {
                case "ativo" -> {
                    long saldo = debito - credito;   // retificadora fica negativa naturalmente
                    if (saldo != 0) {
                        ativo.add(new BalancoResponse.Linha(conta.getCodigo(), conta.getNome(), saldo));
                        totalAtivo += saldo;
                    }
                }
                case "passivo", "pl" -> {
                    long saldo = credito - debito;
                    if (saldo != 0) {
                        passivoPl.add(new BalancoResponse.Linha(conta.getCodigo(), conta.getNome(), saldo));
                        totalPassivoPl += saldo;
                    }
                }
                case "receita" -> resultado += (credito - debito);
                case "custo", "despesa" -> resultado -= (debito - credito);
                default -> { /* ignora */ }
            }
        }

        // Resultado do exercício ainda não encerrado entra no PL (para o balanço fechar).
        if (resultado != 0) {
            passivoPl.add(new BalancoResponse.Linha("—", "Resultado do Exercício (a apurar)", resultado));
            totalPassivoPl += resultado;
        }

        ativo.sort(Comparator.comparing(BalancoResponse.Linha::getCodigo));
        passivoPl.sort(Comparator.comparing(BalancoResponse.Linha::getCodigo));
        return new BalancoResponse(data, ativo, passivoPl, totalAtivo, totalPassivoPl, resultado);
    }

    private static final LocalDate MIN = LocalDate.of(1900, 1, 1);

    public RazaoResponse razao(String codigo, LocalDate de, LocalDate ate) {
        ContaContabil conta = contaService.buscarPorCodigo(codigo);
        boolean devedora = "D".equals(conta.getNatureza());

        // Saldo inicial: posição da conta ANTES do período (na direção natural da conta).
        long saldoInicial = saldoNatural(conta.getId(), MIN, de.minusDays(1), devedora);
        long saldo = saldoInicial;

        List<RazaoResponse.Linha> linhas = new ArrayList<>();
        for (Object[] row : partidaRepository.razaoDaConta(conta.getId(), de, ate)) {
            LocalDate data = toLocalDate(row[0]);
            Long numero = row[1] == null ? null : num(row[1]);
            String historico = row[2] == null ? null : String.valueOf(row[2]);
            String tipo = String.valueOf(row[3]);   // H2 pode devolver VARCHAR(1) como Character
            long valor = num(row[4]);
            long delta = devedora
                    ? ("D".equals(tipo) ? valor : -valor)
                    : ("C".equals(tipo) ? valor : -valor);
            saldo += delta;
            linhas.add(new RazaoResponse.Linha(data, numero, historico, tipo, valor, saldo));
        }
        return new RazaoResponse(conta.getCodigo(), conta.getNome(), de, ate, linhas, saldoInicial, saldo);
    }

    /** Saldo de uma conta na janela, na direção natural (devedora: D−C; credora: C−D). */
    private long saldoNatural(Long contaId, LocalDate de, LocalDate ate, boolean devedora) {
        List<Object[]> r = partidaRepository.somarConta(contaId, de, ate);
        if (r.isEmpty()) return 0;
        long debito = num(r.get(0)[0]);
        long credito = num(r.get(0)[1]);
        return devedora ? (debito - credito) : (credito - debito);
    }

    /** Livro Diário: lançamentos do período em ordem cronológica, cada um com suas partidas. */
    public DiarioResponse diario(LocalDate de, LocalDate ate) {
        List<DiarioResponse.Lancamento> lancamentos = new ArrayList<>();
        DiarioResponse.Lancamento atual = null;
        Long numeroAtual = null;
        for (Object[] row : partidaRepository.diario(de, ate)) {
            long numero = num(row[0]);
            if (atual == null || !Long.valueOf(numero).equals(numeroAtual)) {
                atual = new DiarioResponse.Lancamento(numero, toLocalDate(row[1]),
                        row[2] == null ? null : String.valueOf(row[2]));
                lancamentos.add(atual);
                numeroAtual = numero;
            }
            String codigo = String.valueOf(row[3]);
            String nome = String.valueOf(row[4]);
            String tipo = String.valueOf(row[5]);
            long valor = num(row[6]);
            atual.getPartidas().add(new DiarioResponse.Partida(codigo, nome, tipo, valor));
            if ("D".equals(tipo)) atual.addDebito(valor); else atual.addCredito(valor);
        }
        return new DiarioResponse(de, ate, lancamentos);
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof LocalDate ld) return ld;
        return LocalDate.parse(o.toString());
    }
}
