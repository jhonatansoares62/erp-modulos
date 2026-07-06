package br.com.erpkit.contabil.dto;

import java.time.LocalDate;

/** Base do período para apurar CMV: EI, Compras e Estoque disponível (EI + Compras). */
public class InventarioBaseResponse {

    private LocalDate de;
    private LocalDate ate;
    private long estoqueInicialCentavos;
    private long comprasCentavos;
    private long estoqueDisponivelCentavos;

    public InventarioBaseResponse(LocalDate de, LocalDate ate, long estoqueInicialCentavos,
                                  long comprasCentavos, long estoqueDisponivelCentavos) {
        this.de = de;
        this.ate = ate;
        this.estoqueInicialCentavos = estoqueInicialCentavos;
        this.comprasCentavos = comprasCentavos;
        this.estoqueDisponivelCentavos = estoqueDisponivelCentavos;
    }

    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }
    public long getEstoqueInicialCentavos() { return estoqueInicialCentavos; }
    public long getComprasCentavos() { return comprasCentavos; }
    public long getEstoqueDisponivelCentavos() { return estoqueDisponivelCentavos; }
}
