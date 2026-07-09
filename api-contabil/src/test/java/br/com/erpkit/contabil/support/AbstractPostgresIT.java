package br.com.erpkit.contabil.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: sobem contra um Postgres REAL via Testcontainers, com o Flyway
 * rodando as migrations nativas (V1..Vn) do zero — teste = produção, sem H2 e sem drift de checksum.
 *
 * <p>Container singleton (start no bloco estático, reaproveitado por todas as classes na mesma JVM;
 * o Ryuk do Testcontainers derruba no shutdown). Como todos os testes de banco são {@code @Transactional}
 * (rollback), um único container/schema serve a suíte inteira. Usa a MESMA major do Postgres do homolog.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
