package br.com.erpkit.contabil;

import br.com.erpkit.contabil.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: o contexto Spring sobe e o Flyway aplica TODAS as migrations NATIVAS (V1..Vn) no schema
 * 'contabil' de um Postgres real (Testcontainers), com a validação do Hibernate passando — teste = produção.
 */
class ContabilApplicationTests extends AbstractPostgresIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayAplicouTodasAsMigrationsNativas() {
        Integer aplicadas = jdbc.queryForObject(
                "SELECT count(*) FROM contabil.flyway_schema_history WHERE success = true AND version IS NOT NULL",
                Integer.class);
        assertThat(aplicadas).isGreaterThanOrEqualTo(20);   // V1..V20 nativas (incl. V16 PRIMARY KEY DEFAULT / V17 lower(email))

        // Coluna criada pela V19 (encerramento): prova que as migrations recentes rodaram no Postgres real.
        Integer colunaTipoV19 = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                        "WHERE table_schema = 'contabil' AND table_name = 'lancamento' AND column_name = 'tipo'",
                Integer.class);
        assertThat(colunaTipoV19).isEqualTo(1);
    }
}
