package br.com.erpkit.contabil.dto;

import java.math.BigDecimal;

/** Partida de um roteiro, com a conta já resolvida (código + nome) quando modo constante. */
public class RegraPartidaResponse {

    private String tipo;          // D | C
    private String contaModo;     // constante | variavel
    private String contaCodigo;   // null quando modo variavel
    private String contaNome;     // null quando modo variavel
    private String contaCampo;    // preenchido quando modo variavel
    private String base;          // valor_total | percentual
    private BigDecimal percentual;

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getContaModo() { return contaModo; }
    public void setContaModo(String contaModo) { this.contaModo = contaModo; }

    public String getContaCodigo() { return contaCodigo; }
    public void setContaCodigo(String contaCodigo) { this.contaCodigo = contaCodigo; }

    public String getContaNome() { return contaNome; }
    public void setContaNome(String contaNome) { this.contaNome = contaNome; }

    public String getContaCampo() { return contaCampo; }
    public void setContaCampo(String contaCampo) { this.contaCampo = contaCampo; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public BigDecimal getPercentual() { return percentual; }
    public void setPercentual(BigDecimal percentual) { this.percentual = percentual; }
}
