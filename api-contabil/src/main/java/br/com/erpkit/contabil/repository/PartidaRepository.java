package br.com.erpkit.contabil.repository;

import br.com.erpkit.contabil.model.Partida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PartidaRepository extends JpaRepository<Partida, Long> {

    List<Partida> findByLancamentoId(Long lancamentoId);

    List<Partida> findByContaId(Long contaId);

    /** Soma débitos e créditos por conta, de lançamentos postados no período. [contaId, debito, credito]. */
    @Query(value = """
            SELECT p.conta_id AS conta_id,
                   COALESCE(SUM(CASE WHEN p.tipo = 'D' THEN p.valor_centavos ELSE 0 END), 0) AS debito,
                   COALESCE(SUM(CASE WHEN p.tipo = 'C' THEN p.valor_centavos ELSE 0 END), 0) AS credito
            FROM contabil.partida p
            JOIN contabil.lancamento l ON l.id = p.lancamento_id
            WHERE l.status <> 'rascunho' AND l.data_competencia BETWEEN :de AND :ate
            GROUP BY p.conta_id
            """, nativeQuery = true)
    List<Object[]> somarPorConta(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Movimentos de uma conta no período, em ordem cronológica (para o razão).
     *  [data_competencia, numero, historico, tipo, valor_centavos]. */
    @Query(value = """
            SELECT l.data_competencia AS data_competencia, l.numero AS numero,
                   l.historico AS historico, p.tipo AS tipo, p.valor_centavos AS valor_centavos
            FROM contabil.partida p
            JOIN contabil.lancamento l ON l.id = p.lancamento_id
            WHERE p.conta_id = :contaId AND l.status <> 'rascunho'
              AND l.data_competencia BETWEEN :de AND :ate
            ORDER BY l.data_competencia, l.numero
            """, nativeQuery = true)
    List<Object[]> razaoDaConta(@Param("contaId") Long contaId,
                                @Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
