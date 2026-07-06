package br.com.erpkit.contabil.dto;

import br.com.erpkit.contabil.model.ContaContabil;

/** Representação de uma conta do plano de contas. */
public class ContaResponse {

    private Long id;
    private String codigo;
    private String nome;
    private String tipo;
    private String natureza;
    private String grupo;
    private boolean retificadora;
    private int nivel;
    private boolean aceitaLancamento;
    private boolean ativo;

    public static ContaResponse de(ContaContabil c) {
        ContaResponse r = new ContaResponse();
        r.id = c.getId();
        r.codigo = c.getCodigo();
        r.nome = c.getNome();
        r.tipo = c.getTipo();
        r.natureza = c.getNatureza();
        r.grupo = c.getGrupo();
        r.retificadora = c.isRetificadora();
        r.nivel = c.getNivel();
        r.aceitaLancamento = c.isAceitaLancamento();
        r.ativo = c.isAtivo();
        return r;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public String getTipo() { return tipo; }
    public String getNatureza() { return natureza; }
    public String getGrupo() { return grupo; }
    public boolean isRetificadora() { return retificadora; }
    public int getNivel() { return nivel; }
    public boolean isAceitaLancamento() { return aceitaLancamento; }
    public boolean isAtivo() { return ativo; }
}
