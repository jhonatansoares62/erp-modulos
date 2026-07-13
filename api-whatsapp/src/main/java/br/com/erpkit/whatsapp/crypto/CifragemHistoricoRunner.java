package br.com.erpkit.whatsapp.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Backfill idempotente: cifra em repouso o {@code conteudo} LEGADO (texto plano) de
 * {@code mensagens_log} — as linhas que ja existiam antes do {@code @Convert} entrar
 * (item 2 LGPD). Roda uma vez no boot e se auto-cura.
 *
 * <p><b>Deteccao por SQL nativo, de proposito</b> (fura o converter): seleciona as
 * linhas cujo valor CRU nao tem o prefixo {@code v1:}. Le cru → cifra em Java
 * ({@link CampoCripto}) → grava cru pelo {@code id}. Toca SO a coluna {@code conteudo}
 * (nao mexe em wamid/criado_em/etc.). Depois do 1o boot todas viram {@code v1:...} e a
 * query nao acha mais nada — no-op nos boots seguintes (e nos testes, tabela vazia).
 *
 * <p><b>Nunca derruba o boot:</b> roda em lotes; qualquer falha (ex.: chave indisponivel)
 * e logada e retentada no proximo boot — o conteudo legado continua legivel enquanto isso
 * (o converter devolve texto plano sem prefixo como esta).
 */
@Component
public class CifragemHistoricoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CifragemHistoricoRunner.class);
    private static final int LOTE = 500;

    private final JdbcTemplate jdbc;

    public CifragemHistoricoRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int total = 0;
            while (true) {
                // conteudo <> '' evita loop infinito: cifrar('') devolve '' (nao ganha prefixo v1:).
                List<long[]> ids = jdbc.query(
                        "SELECT id FROM whatsapp.mensagens_log "
                      + "WHERE conteudo IS NOT NULL AND conteudo <> '' AND conteudo NOT LIKE 'v1:%' "
                      + "ORDER BY id LIMIT " + LOTE,
                        (rs, n) -> new long[]{ rs.getLong(1) });
                if (ids.isEmpty()) {
                    break;
                }
                for (long[] linha : ids) {
                    long id = linha[0];
                    String plano = jdbc.queryForObject(
                            "SELECT conteudo FROM whatsapp.mensagens_log WHERE id = ?", String.class, id);
                    jdbc.update("UPDATE whatsapp.mensagens_log SET conteudo = ? WHERE id = ?",
                            CampoCripto.cifrar(plano), id);
                    total++;
                }
                if (ids.size() < LOTE) {
                    break;
                }
            }
            if (total > 0) {
                log.warn("Backfill de cripto: {} mensagens do historico cifradas em repouso "
                       + "(mensagens_log.conteudo).", total);
            }
        } catch (RuntimeException e) {
            log.error("Falha no backfill de cripto do mensagens_log (retentado no proximo boot): {}",
                    e.getMessage());
        }
    }
}
