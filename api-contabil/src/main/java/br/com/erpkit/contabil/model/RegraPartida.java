package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Linha de partida de um roteiro. Mapeia contabil.regra_partida (V1).
 * contaModo = 'constante' (contaId) | 'variavel' (contaCampo lido do contexto do evento).
 */
@Entity
@Table(schema = "contabil", name = "regra_partida")
public class RegraPartida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "regra_id", nullable = false)
    private Long regraId;

    /** D | C */
    @Column(name = "tipo", nullable = false, length = 1)
    private String tipo;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    /** constante | variavel */
    @Column(name = "conta_modo", nullable = false, length = 20)
    private String contaModo;

    @Column(name = "conta_id")
    private Long contaId;

    /** Caminho no contexto do evento quando modo=variavel (ex.: clienteRef.contaContabilId). */
    @Column(name = "conta_campo", length = 120)
    private String contaCampo;

    /** valor_total | percentual */
    @Column(name = "base", nullable = false, length = 20)
    private String base = "valor_total";

    @Column(name = "percentual")
    private BigDecimal percentual;

    public RegraPartida() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRegraId() { return regraId; }
    public void setRegraId(Long regraId) { this.regraId = regraId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }

    public String getContaModo() { return contaModo; }
    public void setContaModo(String contaModo) { this.contaModo = contaModo; }

    public Long getContaId() { return contaId; }
    public void setContaId(Long contaId) { this.contaId = contaId; }

    public String getContaCampo() { return contaCampo; }
    public void setContaCampo(String contaCampo) { this.contaCampo = contaCampo; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public BigDecimal getPercentual() { return percentual; }
    public void setPercentual(BigDecimal percentual) { this.percentual = percentual; }
}
