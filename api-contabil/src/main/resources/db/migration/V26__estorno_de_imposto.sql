-- V26: devolução do imposto quando a venda é cancelada.
--
-- Faltava o par do imposto.apurado. A venda gerava
--   D (-) Deduções de Tributos · C Simples Nacional a Recolher
-- e o estorno desfazia a receita, o caixa e (desde a V25) o custo — mas o imposto
-- ficava. Sobrava Simples a Recolher de uma venda que não existe mais, ou seja, o
-- viveiro pagaria imposto sobre dinheiro que devolveu ao cliente.
--
-- No Simples Nacional a devolução reduz a base do período, então o certo é
-- desfazer os dois lados, exatamente ao contrário do que a apuração lançou.
--
-- Compatibilidade com o Odonto: tipo de evento novo, emitido só pelo Mudas. Não
-- altera o casamento de nenhum roteiro existente.

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo)
VALUES ('imposto.estornado', 10, '{}',
        'Estorno do Simples s/ venda {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'imposto.estornado' AND r.prioridade = 10 AND c.codigo = '2.1.3.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'imposto.estornado' AND r.prioridade = 10 AND c.codigo = '3.1.1.04';
