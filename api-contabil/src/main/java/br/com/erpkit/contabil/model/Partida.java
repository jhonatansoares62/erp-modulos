package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Partida (linha D/C) de um lançamento. Mapeia contabil.partida (V1).
 * Invariante (aplicação): por lançamento, sum(D) = sum(C). Só posta em conta analítica.
 */
@Entity
@Table(schema = "contabil", name = "partida")
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lancamento_id", nullable = false)
    private Long lancamentoId;

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    /** D | C */
    @Column(name = "tipo", nullable = false, length = 1)
    private String tipo;

    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    public Partida() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLancamentoId() { return lancamentoId; }
    public void setLancamentoId(Long lancamentoId) { this.lancamentoId = lancamentoId; }

    public Long getContaId() { return contaId; }
    public void setContaId(Long contaId) { this.contaId = contaId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(long valorCentavos) { this.valorCentavos = valorCentavos; }

    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
}
