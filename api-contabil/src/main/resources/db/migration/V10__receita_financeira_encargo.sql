-- V10: receita financeira (juros/multa por atraso no recebimento).
-- Conta 3.1.2.01 Receitas Financeiras + roteiros encargo.recebido (D conta de liquidação /
-- C Receitas Financeiras). O encargo é um evento separado do recebimento: a parcela baixa
-- pelo valor de face (recebimento.baixado) e o encargo credita a receita financeira.

-- Conta sintética 3.1.2 e analítica 3.1.2.01 (Receitas, credora).
INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, pai_id, nivel, aceita_lancamento, ativo)
VALUES ('3.1.2', 'Receitas Financeiras', 'sintetica', 'C', 'receita', FALSE,
        (SELECT id FROM contabil.conta_contabil WHERE codigo='3.1'), 3, FALSE, TRUE);
INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, pai_id, nivel, aceita_lancamento, ativo)
VALUES ('3.1.2.01', 'Receitas Financeiras', 'analitica', 'C', 'receita', FALSE,
        (SELECT id FROM contabil.conta_contabil WHERE codigo='3.1.2'), 4, TRUE, TRUE);

-- Roteiros encargo.recebido por conta de liquidação (D <conta> / C 3.1.2.01 Receitas Financeiras).
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('encargo.recebido', 30, '{"contaLiquidacao":"1.1.1.01"}', 'Encargo {numero} em dinheiro', 1, TRUE),
 ('encargo.recebido', 30, '{"contaLiquidacao":"1.1.1.02"}', 'Encargo {numero} em banco',    1, TRUE),
 ('encargo.recebido', 30, '{"contaLiquidacao":"1.1.2.02"}', 'Encargo {numero} em cartao',   1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='encargo.recebido' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='encargo.recebido' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='3.1.2.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='encargo.recebido' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.1.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='encargo.recebido' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='3.1.2.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='encargo.recebido' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='1.1.2.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='encargo.recebido' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='3.1.2.01';
