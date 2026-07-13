package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de auditoria de acesso ao dado do paciente no inbox (LGPD item 3, V14).
 * Grava QUEM (atendente) acessou o dado de QUEM (telefone) e QUANDO.
 *
 * <p>{@code atendenteEmail} pode ser {@code null} (acesso via X-API-Key = ERP/sistema,
 * sem atendente humano). {@code criadoEm} e gerenciado pelo banco ({@code DEFAULT NOW()}).
 */
@Entity
@Table(schema = "whatsapp", name = "auditoria_acesso")
public class AuditoriaAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "atendente_email", length = 160)
    private String atendenteEmail;

    @Column(name = "acao", nullable = false, length = 40)
    private String acao;

    @Column(name = "telefone_alvo", length = 20)
    private String telefoneAlvo;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private Instant criadoEm;

    protected AuditoriaAcesso() {
        // JPA exige construtor padrao
    }

    public AuditoriaAcesso(String atendenteEmail, String acao, String telefoneAlvo) {
        this.atendenteEmail = atendenteEmail;
        this.acao = acao;
        this.telefoneAlvo = telefoneAlvo;
    }

    public Long getId() { return id; }
    public String getAtendenteEmail() { return atendenteEmail; }
    public String getAcao() { return acao; }
    public String getTelefoneAlvo() { return telefoneAlvo; }
    public Instant getCriadoEm() { return criadoEm; }
}
