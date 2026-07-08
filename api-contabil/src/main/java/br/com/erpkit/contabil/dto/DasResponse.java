package br.com.erpkit.contabil.dto;

import java.util.List;

/**
 * Estado do DAS: saldo total a recolher (credor de 2.1.3.01), sugestão para a competência
 * consultada (movimento líquido do mês = imposto do período ainda em aberto) e histórico.
 */
public class DasResponse {

    private final long saldoCentavos;
    private final String competencia;
    private final Long saldoCompetenciaCentavos;
    private final boolean competenciaPaga;
    private final List<PagamentoDasResponse> historico;

    public DasResponse(long saldoCentavos, String competencia, Long saldoCompetenciaCentavos,
                       boolean competenciaPaga, List<PagamentoDasResponse> historico) {
        this.saldoCentavos = saldoCentavos;
        this.competencia = competencia;
        this.saldoCompetenciaCentavos = saldoCompetenciaCentavos;
        this.competenciaPaga = competenciaPaga;
        this.historico = historico;
    }

    public long getSaldoCentavos() { return saldoCentavos; }
    public String getCompetencia() { return competencia; }
    public Long getSaldoCompetenciaCentavos() { return saldoCompetenciaCentavos; }
    public boolean isCompetenciaPaga() { return competenciaPaga; }
    public List<PagamentoDasResponse> getHistorico() { return historico; }
}
