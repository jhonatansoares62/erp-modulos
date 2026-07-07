-- V11: devoluções/cancelamentos de venda separados das deduções de tributos.
-- Conta 3.1.1.05 (-) Devoluções/Cancelamentos de Vendas (retificadora de receita), usada no
-- estorno de venda tratado como devolução: D 3.1.1.05 / C 1.1.2.01 Clientes a Receber.
-- 3.1.1.04 passa a ser SÓ tributos.

UPDATE contabil.conta_contabil SET nome = '(-) Deducoes de Tributos'
 WHERE codigo = '3.1.1.04';

INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, pai_id, nivel, aceita_lancamento, ativo)
VALUES ('3.1.1.05', '(-) Devolucoes/Cancelamentos de Vendas', 'analitica', 'D', 'receita', TRUE,
        (SELECT id FROM contabil.conta_contabil WHERE codigo='3.1.1'), 4, TRUE, TRUE);

-- Roteiro da devolução: D 3.1.1.05 (-) Devoluções / C 1.1.2.01 Clientes a Receber.
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('venda.devolvida', 10, NULL, 'Devolucao/cancelamento da venda {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.devolvida' AND r.prioridade=10 AND c.codigo='3.1.1.05';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.devolvida' AND r.prioridade=10 AND c.codigo='1.1.2.01';
