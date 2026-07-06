-- V9: competência da despesa (via passivo Fornecedores) + baixa roteada por forma.
-- Substitui o roteiro antigo "despesa.incorrida = D Despesa / C Caixa (vale sempre)" por:
--   despesa.incorrida  -> D <conta de resultado do tipo> / C 2.1.1.01 Fornecedores a Pagar
--   pagamento.efetuado -> D 2.1.1.01 Fornecedores / C <conta de liquidação da forma>
-- As contas de resultado (débito) são parametrizadas por tipo de despesa no ERP
-- (ctx.contaResultado); a conta de liquidação reusa o mapeamento por forma do Item 4.

-- Conta analítica nova: 3.2.1.02 Custo dos serviços (comissão dentista, laboratório).
INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, pai_id, nivel, aceita_lancamento, ativo)
VALUES ('3.2.1.02', 'Custo dos serviços', 'analitica', 'D', 'custo', FALSE,
        (SELECT id FROM contabil.conta_contabil WHERE codigo='3.2.1'), 4, TRUE, TRUE);

-- Aposenta o roteiro antigo "vale sempre" (D Despesa / C Caixa).
UPDATE contabil.regra_lancamento SET ativo = FALSE
 WHERE evento_tipo='despesa.incorrida' AND (condicoes IS NULL OR condicoes='');

-- despesa.incorrida por conta de resultado (D <conta> / C 2.1.1.01 Fornecedores).
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('despesa.incorrida', 30, '{"contaResultado":"3.2.2.01"}', 'Despesa administrativa {numero}', 1, TRUE),
 ('despesa.incorrida', 30, '{"contaResultado":"3.2.2.02"}', 'Despesa com vendas {numero}',     1, TRUE),
 ('despesa.incorrida', 30, '{"contaResultado":"3.2.1.02"}', 'Custo do servico {numero}',       1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=30 AND r.condicoes='{"contaResultado":"3.2.2.01"}' AND c.codigo='3.2.2.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=30 AND r.condicoes='{"contaResultado":"3.2.2.01"}' AND c.codigo='2.1.1.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=30 AND r.condicoes='{"contaResultado":"3.2.2.02"}' AND c.codigo='3.2.2.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=30 AND r.condicoes='{"contaResultado":"3.2.2.02"}' AND c.codigo='2.1.1.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=30 AND r.condicoes='{"contaResultado":"3.2.1.02"}' AND c.codigo='3.2.1.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=30 AND r.condicoes='{"contaResultado":"3.2.1.02"}' AND c.codigo='2.1.1.01';

-- pagamento.efetuado roteado pela conta de liquidação (D 2.1.1.01 Fornecedores / C <conta>).
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('pagamento.efetuado', 30, '{"contaLiquidacao":"1.1.1.01"}', 'Pagamento {numero} em dinheiro', 1, TRUE),
 ('pagamento.efetuado', 30, '{"contaLiquidacao":"1.1.1.02"}', 'Pagamento {numero} em banco',    1, TRUE),
 ('pagamento.efetuado', 30, '{"contaLiquidacao":"1.1.2.02"}', 'Pagamento {numero} em cartao',   1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='2.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.1.01';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='2.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.1.02';

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='2.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=30 AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='1.1.2.02';
