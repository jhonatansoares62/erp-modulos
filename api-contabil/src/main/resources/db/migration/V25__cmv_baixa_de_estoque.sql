-- V25: custo da mercadoria vendida.
--
-- Faltava o outro lado da venda. A compra debita 1.1.3.01 Estoque, a venda
-- reconhece a receita — e nada nunca creditava o Estoque de volta. O resultado é
-- um ativo que só cresce, por mais que se venda, e um lucro maior do que o real,
-- porque a receita fica sem o custo que a produziu.
--
-- Dois eventos novos, espelhados:
--   cmv.reconhecido → D Custo das mercadorias · C Estoque
--   cmv.estornado   → D Estoque · C Custo das mercadorias  (devolução)
--
-- Compatibilidade com o Odonto: são tipos de evento que só o Mudas emite. Regra
-- nova para evento novo não altera o casamento de nenhum roteiro existente, e o
-- Odonto segue sem lançar CMV como sempre esteve.
--
-- Quem manda o valor é o ERP: é ele que sabe o custo cadastrado de cada item. O
-- evento só é emitido quando há custo, então venda de item sem custo cadastrado
-- não gera lançamento nenhum — em vez de afirmar que a mercadoria custou zero.

-- Baixa do estoque pela venda.
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo)
VALUES ('cmv.reconhecido', 10, '{}',
        'Custo das mercadorias da venda {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'cmv.reconhecido' AND r.prioridade = 10 AND c.codigo = '3.2.1.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'cmv.reconhecido' AND r.prioridade = 10 AND c.codigo = '1.1.3.01';

-- Devolução: a mercadoria voltou pro estoque.
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo)
VALUES ('cmv.estornado', 10, '{}',
        'Retorno ao estoque da venda {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'cmv.estornado' AND r.prioridade = 10 AND c.codigo = '1.1.3.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'cmv.estornado' AND r.prioridade = 10 AND c.codigo = '3.2.1.01';
