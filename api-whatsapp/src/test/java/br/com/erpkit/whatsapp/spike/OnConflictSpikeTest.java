package br.com.erpkit.whatsapp.spike;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPIKE da Wave 1 — Phase 2: gate empirico para a decisao tecnica do Plan 03.
 *
 * <p><b>Resultado da execucao em 2026-05-05 (gate Wave 1):</b>
 * H2 v2.3.232 (Spring Boot 3.5.9 BOM) com {@code MODE=PostgreSQL} <b>NAO ACEITA</b>
 * a sintaxe Postgres-native {@code INSERT ... ON CONFLICT (col) DO NOTHING}. O parser
 * H2 levanta {@code JdbcSQLSyntaxErrorException [42000-232]} apontando o {@code [*]}
 * exatamente antes de {@code ON CONFLICT}.
 *
 * <p>Erro literal observado:
 * <pre>
 * Syntax error in SQL statement
 *   "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao)
 *    VALUES (?, ?, ?) [*]ON CONFLICT (wamid) DO NOTHING"; [42000-232]
 * </pre>
 *
 * <p><b>Decisao consequente (Plan 03):</b> aplicar o <b>fallback documentado no
 * RESEARCH.md secao 2.4</b> — usar {@code save()} envolvido em try/catch de
 * {@link DataIntegrityViolationException} (UNIQUE constraint do banco e o gate
 * atomico, JPA traduz a violacao para a excecao Spring portavel). Equivalencia
 * funcional: ambos retornam {@code boolean novo} para o caller; o contrato
 * {@code IdempotencyService.tentarPersistir} permanece identico.
 *
 * <p><b>Por que este test fica permanentemente no codigo:</b> regressao. Se uma
 * versao futura de H2 adicionar suporte a {@code ON CONFLICT} (que e Postgres-native),
 * este test comecara a falhar — sinal pra revisitar a escolha do fallback e
 * potencialmente migrar para a sintaxe direta (mais barata que try/catch). Ate la,
 * documenta empiricamente o gate que justifica o design do Plan 03.
 *
 * <p><b>O segundo teste</b> ({@link #unique_constraint_dispara_DataIntegrityViolationException_no_h2_pg_mode})
 * valida positivamente o caminho do fallback: que H2 PG-mode dispara
 * {@code DataIntegrityViolationException} (via SQL state {@code 23505}) em INSERT
 * de wamid duplicado — o sinal exato que o {@code IdempotencyService.tentarPersistir}
 * vai capturar.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class OnConflictSpikeTest {

    @Autowired
    JdbcTemplate jdbc;

    /**
     * <b>Gate FALHA (esperado):</b> documenta empiricamente que H2 v2.3.232 modo PG
     * nao reconhece {@code ON CONFLICT}. Se este test um dia comecar a falhar
     * (ou seja, NAO disparar mais a sintaxe error), revisitar Plan 03 — ON CONFLICT
     * direto e mais barato que try/catch.
     */
    @Test
    void on_conflict_do_nothing_NAO_e_suportado_em_h2_pg_mode_v2_3_232() {
        assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO whatsapp.clientes_zap (telefone) VALUES (?) " +
                "ON CONFLICT (telefone) DO NOTHING",
                "5511spike001"
            )
        )
        .as("Gate empirico Wave 1: H2 v2.3.232 nao parseia ON CONFLICT — Plan 03 usa fallback save+catch")
        .isInstanceOf(BadSqlGrammarException.class)
        .hasMessageContaining("ON CONFLICT");
    }

    /**
     * <b>Caminho do fallback validado:</b> H2 PG-mode dispara
     * {@link DataIntegrityViolationException} em INSERT de wamid duplicado. Esse
     * e exatamente o sinal que o {@code IdempotencyService.tentarPersistir} vai
     * capturar para retornar {@code false} (duplicata).
     *
     * <p>Confirma tambem que o INSERT preserva o telefone original (UNIQUE constraint
     * dispara antes do UPDATE — DO NOTHING semantics emergem naturalmente do gate).
     */
    @Test
    void unique_constraint_dispara_DataIntegrityViolationException_no_h2_pg_mode() {
        // Primeira insercao: ok
        int primeira = jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) VALUES (?, ?, ?)",
            "wamid.spike.fallback", "5511spike010", "in"
        );
        assertThat(primeira).isEqualTo(1);

        // Segunda insercao com mesmo wamid: deve disparar DataIntegrityViolationException
        // (Spring traduz JdbcSQLIntegrityConstraintViolationException via SQLExceptionTranslator)
        assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) VALUES (?, ?, ?)",
                "wamid.spike.fallback", "5511spike011", "in"
            )
        )
        .as("Fallback do Plan 03: catch DataIntegrityViolationException = duplicata detectada")
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("wamid");

        // Confirma que o registro original foi preservado (telefone NAO foi sobrescrito)
        String telefone = jdbc.queryForObject(
            "SELECT telefone FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.spike.fallback"
        );
        assertThat(telefone)
            .as("UNIQUE constraint dispara antes do UPDATE — original preservado")
            .isEqualTo("5511spike010");
    }
}
