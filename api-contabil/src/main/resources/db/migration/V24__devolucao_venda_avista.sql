-- V24: devolução de venda que foi paga à vista.
--
-- O roteiro existente (V11, prioridade 10) credita 1.1.2.01 Clientes a Receber,
-- assumindo que a venda virou recebível — que é sempre o caso no Odonto, onde
-- tudo passa por conta a receber.
--
-- No ERP Mudas a venda à vista debita Caixa/Bancos direto contra a receita, sem
-- passar por recebível. Devolvê-la pelo roteiro antigo creditaria um recebível
-- que nunca foi debitado: sobraria saldo credor em Clientes a Receber e o
-- dinheiro devolvido continuaria no Caixa. Por isso o Mudas simplesmente não
-- emitia o evento nesse caso — melhor não lançar do que lançar errado.
--
-- Aqui entram as duas regras que faltavam, espelhando exatamente as de
-- venda.finalizada: devolver à vista credita a conta de onde o dinheiro saiu.
--
-- Compatibilidade com o Odonto: o casamento de regra ordena por prioridade DESC
-- e exige que TODAS as condições batam com o contexto do evento. O Odonto emite
-- venda.devolvida com contexto vazio, então nunca casa com condicao=avista e
-- continua caindo na regra genérica de prioridade 10, inalterada.

-- À vista em dinheiro (ou forma não identificada): D (-) Devoluções · C Caixa.
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo)
VALUES ('venda.devolvida', 20, '{"condicao":"avista"}',
        'Devolucao da venda {numero} em dinheiro', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'venda.devolvida' AND r.prioridade = 20 AND c.codigo = '3.1.1.05';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'venda.devolvida' AND r.prioridade = 20 AND c.codigo = '1.1.1.01';

-- À vista em pix: o dinheiro saiu do banco, então é o banco que devolve.
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo)
VALUES ('venda.devolvida', 30, '{"condicao":"avista","meioPagamento":"pix"}',
        'Devolucao da venda {numero} via PIX', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'venda.devolvida' AND r.prioridade = 30 AND c.codigo = '3.1.1.05';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total'
  FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo = 'venda.devolvida' AND r.prioridade = 30 AND c.codigo = '1.1.1.02';
