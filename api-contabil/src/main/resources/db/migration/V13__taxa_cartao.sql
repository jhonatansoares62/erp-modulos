-- V13: taxa da adquirente no recebimento por cartão.
-- taxa.cartao: D 3.2.3.01 Despesas Financeiras · C 1.1.2.02 Cartões a Receber.
-- Evento separado do recebimento (que baixa o BRUTO em 1.1.2.02): esta partida reduz o
-- recebível de cartão pela taxa retida, deixando-o pelo LÍQUIDO, e reconhece a despesa
-- financeira. O valor (bruto × %taxa) é calculado no ERP, dono da alíquota; aqui só lança.

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('taxa.cartao', 10, NULL, 'Taxa da adquirente (cartao) parc. {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='taxa.cartao' AND r.prioridade=10 AND c.codigo='3.2.3.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='taxa.cartao' AND r.prioridade=10 AND c.codigo='1.1.2.02';
