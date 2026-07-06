package br.com.erpkit.contabil.dto;

import br.com.erpkit.contabil.model.ContaContabil;

import java.util.ArrayList;
import java.util.List;

/** Nó da árvore do plano de contas (para o painel de configuração). */
public class ContaArvoreNode {

    private Long id;
    private String codigo;
    private String nome;
    private String tipo;
    private String natureza;
    private boolean aceitaLancamento;
    private List<ContaArvoreNode> filhos = new ArrayList<>();

    public static ContaArvoreNode de(ContaContabil c) {
        ContaArvoreNode n = new ContaArvoreNode();
        n.id = c.getId();
        n.codigo = c.getCodigo();
        n.nome = c.getNome();
        n.tipo = c.getTipo();
        n.natureza = c.getNatureza();
        n.aceitaLancamento = c.isAceitaLancamento();
        return n;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public String getTipo() { return tipo; }
    public String getNatureza() { return natureza; }
    public boolean isAceitaLancamento() { return aceitaLancamento; }
    public List<ContaArvoreNode> getFilhos() { return filhos; }
}
