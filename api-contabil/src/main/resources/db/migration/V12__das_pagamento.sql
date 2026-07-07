-- V12: pagamento do DAS (Simples Nacional) — baixa do passivo 2.1.3.01.
-- das.pago: D 2.1.3.01 Simples Nacional a Recolher · C <conta de liquidação da forma>.
-- Espelha o padrão da V9 (pagamento.efetuado) roteado por ctx.contaLiquidacao. Não afeta
-- resultado — é troca de passivo por disponibilidade (reduz Caixa/Bancos). O valor é
-- decidido no ERP (dono do saldo a recolher); a Contabilidade só lança.

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('das.pago', 30, '{"contaLiquidacao":"1.1.1.01"}', 'DAS Simples Nacional {numero} em dinheiro', 1, TRUE),
 ('das.pago', 30, '{"contaLiquidacao":"1.1.1.02"}', 'DAS Simples Nacional {numero} em banco',    1, TRUE),
 ('das.pago', 10, NULL,                              'DAS Simples Nacional {numero}',            1, TRUE);

-- Dinheiro → Caixa (D 2.1.3.01 / C 1.1.1.01)
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='das.pago' AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='2.1.3.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='das.pago' AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.1.01';

-- Demais formas → Bancos (D 2.1.3.01 / C 1.1.1.02)
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='das.pago' AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='2.1.3.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='das.pago' AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.1.02';

-- Fallback (sem forma informada) → Bancos
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='das.pago' AND r.prioridade=10 AND r.condicoes IS NULL AND c.codigo='2.1.3.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='das.pago' AND r.prioridade=10 AND r.condicoes IS NULL AND c.codigo='1.1.1.02';
