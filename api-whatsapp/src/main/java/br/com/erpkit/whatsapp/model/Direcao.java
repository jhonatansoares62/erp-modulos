package br.com.erpkit.whatsapp.model;

/**
 * Direcao da mensagem em {@code mensagens_log}. Mapeada via {@code @Enumerated(STRING)}.
 * Constants em LOWERCASE deliberadamente — Hibernate {@code STRING} mode usa {@link Enum#name()},
 * que em Java e o exato spelling do constant. {@code Direcao.in.name()} = {@code "in"}.
 *
 * <p>O CHECK constraint na V2 migration ({@code direcao IN ('in', 'out')}) bate com
 * essa convencao. Spike STEP 0 da Phase 1 confirmou que H2 PG-mode NAO silencia o CHECK.
 *
 * <p>Identificadores em lowercase violam convencao Java (UPPER_SNAKE_CASE). Trade-off
 * deliberado: simplicidade do mapping JPA vs convencao. Documentar em CONVENTIONS.md
 * se for necessario justificar.
 */
public enum Direcao {
    in,
    out
}
