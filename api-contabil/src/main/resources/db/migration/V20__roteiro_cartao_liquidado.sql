-- V20: liquidação/repasse da adquirente (cartão → banco).
-- cartao.liquidado: D <conta de liquidação (banco/caixa)> · C 1.1.2.02 Cartões a Receber, pelo
-- valor LÍQUIDO repassado pela operadora. O recebível de cartão ficou pelo líquido (bruto − taxa
-- da adquirente, via evento taxa.cartao); esta liquidação zera 1.1.2.02 quando o dinheiro cai.
-- A conta debitada segue ctx.contaLiquidacao (como no recebimento); sem ctx, cai em Bancos.
-- O valor líquido é informado no ERP (data + valor + conta); aqui só se lança.

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('cartao.liquidado', 30, '{"contaLiquidacao":"1.1.1.02"}', 'Liquidacao de cartao (operadora) parc. {numero}', 1, TRUE),
 ('cartao.liquidado', 30, '{"contaLiquidacao":"1.1.1.01"}', 'Liquidacao de cartao (operadora) parc. {numero}', 1, TRUE),
 ('cartao.liquidado', 10, NULL,                              'Liquidacao de cartao (operadora) parc. {numero}', 1, TRUE);

-- Banco (1.1.1.02): D Bancos / C Cartões a Receber
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.1.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.2.02';

-- Caixa (1.1.1.01): D Caixa / C Cartões a Receber
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.2.02';

-- Fallback (sem ctx.contaLiquidacao): D Bancos / C Cartões a Receber
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=10 AND r.condicoes IS NULL AND c.codigo='1.1.1.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=10 AND r.condicoes IS NULL AND c.codigo='1.1.2.02';
