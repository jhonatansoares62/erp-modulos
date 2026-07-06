package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Conta do plano de contas (árvore sintética/analítica). Mapeia contabil.conta_contabil (V1).
 * Só conta 'analitica' com aceitaLancamento=true recebe partida (invariante F5).
 */
@Entity
@Table(schema = "contabil", name = "conta_contabil")
public class ContaContabil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    /** sintetica | analitica */
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    /** D | C */
    @Column(name = "natureza", nullable = false, length = 1)
    private String natureza;

    /** ativo | passivo | pl | receita | custo | despesa */
    @Column(name = "grupo", nullable = false, length = 20)
    private String grupo;

    @Column(name = "retificadora", nullable = false)
    private boolean retificadora;

    @Column(name = "pai_id")
    private Long paiId;

    @Column(name = "nivel", nullable = false)
    private int nivel;

    @Column(name = "aceita_lancamento", nullable = false)
    private boolean aceitaLancamento;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public ContaContabil() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getNatureza() { return natureza; }
    public void setNatureza(String natureza) { this.natureza = natureza; }

    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }

    public boolean isRetificadora() { return retificadora; }
    public void setRetificadora(boolean retificadora) { this.retificadora = retificadora; }

    public Long getPaiId() { return paiId; }
    public void setPaiId(Long paiId) { this.paiId = paiId; }

    public int getNivel() { return nivel; }
    public void setNivel(int nivel) { this.nivel = nivel; }

    public boolean isAceitaLancamento() { return aceitaLancamento; }
    public void setAceitaLancamento(boolean aceitaLancamento) { this.aceitaLancamento = aceitaLancamento; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public Instant getCreatedAt() { return createdAt; }
}
