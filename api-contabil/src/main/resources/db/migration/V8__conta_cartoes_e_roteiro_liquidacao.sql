-- V8: conta Cartões a Receber + roteiros de recebimento por conta de liquidação.
-- A conta de liquidação (Caixa/Bancos/Cartões) passa a ser parametrizada por forma de
-- pagamento no ERP, que envia ctx.contaLiquidacao=<codigo>. Estes roteiros (prioridade 30,
-- acima dos legados por meioPagamento) debitam a conta indicada e creditam Clientes a Receber.

-- Conta analítica nova: 1.1.2.02 Cartões a Receber (Ativo, devedora, filha de 1.1.2).
INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, pai_id, nivel, aceita_lancamento, ativo)
VALUES ('1.1.2.02', 'Cartões a Receber', 'analitica', 'D', 'ativo', FALSE,
        (SELECT id FROM contabil.conta_contabil WHERE codigo='1.1.2'), 4, TRUE, TRUE);

-- Roteiros recebimento.baixado por conta de liquidação (D <conta> / C 1.1.2.01 Clientes a Receber).
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('recebimento.baixado', 30, '{"contaLiquidacao":"1.1.1.01"}', 'Recebimento {numero} em dinheiro',     1, TRUE),
 ('recebimento.baixado', 30, '{"contaLiquidacao":"1.1.1.02"}', 'Recebimento {numero} em banco',        1, TRUE),
 ('recebimento.baixado', 30, '{"contaLiquidacao":"1.1.2.02"}', 'Recebimento {numero} em cartao',       1, TRUE);

-- 1.1.1.01 Caixa
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.2.01';

-- 1.1.1.02 Bancos
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.1.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.2.01';

-- 1.1.2.02 Cartões a Receber
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='1.1.2.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='1.1.2.01';
