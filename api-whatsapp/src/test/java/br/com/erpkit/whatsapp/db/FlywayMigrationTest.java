package br.com.erpkit.whatsapp.db;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valida empiricamente que as 4 migrations Flyway (V1-V4) foram aplicadas
 * no schema 'whatsapp' do H2 in-memory (modo PostgreSQL) durante o boot do
 * SpringBootTest. PLAN-04 / PER-01 / ROADMAP success criterion 5.
 *
 * Descobertas do spike STEP 0 (H2 2.3.232 modo PG):
 *   - INFORMATION_SCHEMA precisa ser referenciado em UPPERCASE (system tables)
 *   - O nome do schema 'whatsapp' fica em lowercase no INFORMATION_SCHEMA
 *     (porque DATABASE_TO_UPPER=false preserve case do CREATE SCHEMA).
 *   - Indices ficam em INFORMATION_SCHEMA.INDEX_COLUMNS (NAO em INDEXES — PG real
 *     usa pg_indexes; aqui usamos a portabilidade do H2 modo PG).
 *   - CHECK constraint NAO e silenciada (A3 do RESEARCH mitigada empiricamente).
 *   - UNIQUE viola via JdbcSQLIntegrityConstraintViolationException (mapeado
 *     pelo Spring para DataIntegrityViolationException, subclass de DataAccessException).
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void todas_as_4_tabelas_existem_em_schema_whatsapp() {
        // Spike confirmou: H2 modo PG preserva 'whatsapp' lowercase no INFORMATION_SCHEMA.
        // Query em UPPERCASE para os nomes das system tables (TABLE_SCHEMA, TABLES).
        List<String> esperadas = List.of(
            "clientes_zap",
            "mensagens_log",
            "media_cache",
            "estado_conversa"
        );
        for (String t : esperadas) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = 'whatsapp' AND TABLE_NAME = ?",
                Integer.class, t
            );
            assertThat(count)
                .as("Tabela %s deve existir em schema whatsapp", t)
                .isEqualTo(1);
        }
    }

    @Test
    void mensagens_log_tem_indices_em_telefone_e_criado_em() {
        // H2 expoe coluna por coluna em INFORMATION_SCHEMA.INDEX_COLUMNS
        // (uma row por (index, column)). Queremos pelo menos 1 row para
        // (mensagens_log, telefone) e (mensagens_log, criado_em).
        Integer telefoneIdx = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS " +
            "WHERE TABLE_SCHEMA = 'whatsapp' AND TABLE_NAME = 'mensagens_log' AND COLUMN_NAME = 'telefone'",
            Integer.class
        );
        assertThat(telefoneIdx)
            .as("Indice em mensagens_log.telefone deve existir")
            .isPositive();

        Integer criadoEmIdx = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS " +
            "WHERE TABLE_SCHEMA = 'whatsapp' AND TABLE_NAME = 'mensagens_log' AND COLUMN_NAME = 'criado_em'",
            Integer.class
        );
        assertThat(criadoEmIdx)
            .as("Indice em mensagens_log.criado_em deve existir")
            .isPositive();

        // wamid tem indice implicito do UNIQUE — confirmado tambem
        Integer wamidIdx = jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS " +
            "WHERE TABLE_SCHEMA = 'whatsapp' AND TABLE_NAME = 'mensagens_log' AND COLUMN_NAME = 'wamid'",
            Integer.class
        );
        assertThat(wamidIdx)
            .as("Indice implicito do UNIQUE em wamid deve existir")
            .isPositive();
    }

    @Test
    void wamid_tem_constraint_unique() {
        // Primeiro insert: deve passar.
        jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) " +
            "VALUES ('w-test-unique-1', '5511999999999', 'in')"
        );

        // Segundo insert com mesmo wamid: deve violar UNIQUE constraint.
        // Spring mapeia JdbcSQLIntegrityConstraintViolationException ->
        // DataIntegrityViolationException (subclass de DataAccessException).
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) " +
            "VALUES ('w-test-unique-1', '5511888888888', 'in')"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void direcao_tem_check_constraint_in_ou_out() {
        // Spike STEP 0 confirmou: H2 2.3.232 modo PG NAO silencia CHECK.
        // 'in' e 'out' devem passar; qualquer outro valor deve violar.
        jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) " +
            "VALUES ('w-check-in', '5511777777777', 'in')"
        );
        jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) " +
            "VALUES ('w-check-out', '5511666666666', 'out')"
        );

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) " +
            "VALUES ('w-check-bad', '5511555555555', 'xx')"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void clientes_zap_tem_unique_em_telefone() {
        jdbc.update(
            "INSERT INTO whatsapp.clientes_zap (telefone) VALUES ('5511444444444')"
        );

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO whatsapp.clientes_zap (telefone) VALUES ('5511444444444')"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void flyway_schema_history_tem_4_versoes_aplicadas_com_sucesso() {
        // Flyway cria 'flyway_schema_history' em default-schema=whatsapp.
        // Cada migration aplicada vira uma row com success=true.
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.flyway_schema_history WHERE success = TRUE",
            Integer.class
        );
        assertThat(total)
            .as("Flyway deve ter aplicado V1-V4 com sucesso")
            .isGreaterThanOrEqualTo(4);

        List<String> versoes = jdbc.queryForList(
            "SELECT version FROM whatsapp.flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
            String.class
        );
        assertThat(versoes).contains("1", "2", "3", "4");
    }
}
