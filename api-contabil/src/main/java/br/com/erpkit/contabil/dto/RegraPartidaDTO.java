package br.com.erpkit.contabil.dto;

import java.math.BigDecimal;

/** Linha de partida de um roteiro (na criação da regra). */
public class RegraPartidaDTO {

    /** D | C */
    private String tipo;

    /** constante | variavel (default constante). */
    private String contaModo = "constante";

    /** Código da conta quando modo=constante (ex.: 1.1.1.01). */
    private String contaCodigo;

    /** Caminho no contexto do evento quando modo=variavel (ex.: clienteRef.contaContabilId). */
    private String contaCampo;

    /** valor_total | percentual (default valor_total). */
    private String base = "valor_total";

    private BigDecimal percentual;

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getContaModo() { return contaModo; }
    public void setContaModo(String contaModo) { this.contaModo = contaModo; }

    public String getContaCodigo() { return contaCodigo; }
    public void setContaCodigo(String contaCodigo) { this.contaCodigo = contaCodigo; }

    public String getContaCampo() { return contaCampo; }
    public void setContaCampo(String contaCampo) { this.contaCampo = contaCampo; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public BigDecimal getPercentual() { return percentual; }
    public void setPercentual(BigDecimal percentual) { this.percentual = percentual; }
}
