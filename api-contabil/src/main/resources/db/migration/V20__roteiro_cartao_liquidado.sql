-- V20: liquidação/repasse da adquirente (cartão → banco).
-- cartao.liquidado: D 1.1.1.02 Bancos · C 1.1.2.02 Cartões a Receber, pelo valor LÍQUIDO
-- repassado pela operadora. O recebível de cartão ficou pelo líquido (bruto − taxa da adquirente,
-- via evento taxa.cartao); esta liquidação zera 1.1.2.02 contra o banco quando o dinheiro cai.
-- O valor líquido é informado no ERP (data + valor + banco); aqui só se lança.

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('cartao.liquidado', 10, NULL, 'Liquidacao de cartao (operadora) parc. {numero}', 1, TRUE);

-- D 1.1.1.02 Bancos
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=10 AND c.codigo='1.1.1.02';
-- C 1.1.2.02 Cartões a Receber
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='cartao.liquidado' AND r.prioridade=10 AND c.codigo='1.1.2.02';
