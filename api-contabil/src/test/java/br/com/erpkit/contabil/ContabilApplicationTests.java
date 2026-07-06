package br.com.erpkit.contabil;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test da Fase 0: o contexto Spring sobe, o Flyway aplica a V1 no schema
 * 'contabil' do H2 e a validação do Hibernate passa (sem @Entity ainda).
 */
@SpringBootTest
@ActiveProfiles("test")
class ContabilApplicationTests {

    @Test
    void contextLoads() {
    }
}
