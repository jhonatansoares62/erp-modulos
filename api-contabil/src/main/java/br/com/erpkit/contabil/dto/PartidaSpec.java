package br.com.erpkit.contabil.dto;

/**
 * Especificação de uma partida explícita (imutável) para postar um lançamento fora de roteiro.
 * Usada pelo encerramento de exercício via LancamentoService.postar(...).
 */
public class PartidaSpec {

    private final Long contaId;
    private final String tipo;   // D | C
    private final long valorCentavos;

    public PartidaSpec(Long contaId, String tipo, long valorCentavos) {
        this.contaId = contaId;
        this.tipo = tipo;
        this.valorCentavos = valorCentavos;
    }

    public Long getContaId() { return contaId; }
    public String getTipo() { return tipo; }
    public long getValorCentavos() { return valorCentavos; }
}
