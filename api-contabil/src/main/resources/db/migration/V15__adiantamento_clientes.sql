-- V15: reconhecimento de receita por competência do serviço (adiantamento de clientes).
-- Para vendas pós-corte, a receita só é reconhecida na conclusão da OS; o dinheiro recebido
-- antes fica represado em 2.1.2.01 Adiantamento de Clientes (passivo). Contas/roteiros:
--   recebimento.adiantamento: D <conta de liquidação> / C 2.1.2.01  (recebido antes da OS)
--   adiantamento.baixado:     D 2.1.2.01 / C 1.1.2.01 Clientes a Receber (na conclusão)
-- A receita em si reusa venda.finalizada (D Clientes a Receber / C Receita), disparada na
-- conclusão para pós-corte. Histórico (pré-corte) não muda.

-- Conta analítica nova: 2.1.2.01 Adiantamento de Clientes (Passivo Circulante, credora).
INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, pai_id, nivel, aceita_lancamento, ativo)
VALUES ('2.1.2', 'Adiantamentos de Clientes', 'sintetica', 'C', 'passivo', FALSE,
        (SELECT id FROM contabil.conta_contabil WHERE codigo='2.1'), 3, FALSE, TRUE);
INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, pai_id, nivel, aceita_lancamento, ativo)
VALUES ('2.1.2.01', 'Adiantamento de Clientes', 'analitica', 'C', 'passivo', FALSE,
        (SELECT id FROM contabil.conta_contabil WHERE codigo='2.1.2'), 4, TRUE, TRUE);

-- recebimento.adiantamento por conta de liquidação (D <conta> / C 2.1.2.01 Adiantamento).
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('recebimento.adiantamento', 30, '{"contaLiquidacao":"1.1.1.01"}', 'Adiantamento {numero} em dinheiro', 1, TRUE),
 ('recebimento.adiantamento', 30, '{"contaLiquidacao":"1.1.1.02"}', 'Adiantamento {numero} em banco',    1, TRUE),
 ('recebimento.adiantamento', 30, '{"contaLiquidacao":"1.1.2.02"}', 'Adiantamento {numero} em cartao',   1, TRUE),
 ('recebimento.adiantamento', 10, NULL,                              'Adiantamento {numero}',             1, TRUE);

-- Dinheiro -> Caixa
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='1.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.condicoes='{"contaLiquidacao":"1.1.1.01"}' AND c.codigo='2.1.2.01';

-- Bancos -> Bancos
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='1.1.1.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.condicoes='{"contaLiquidacao":"1.1.1.02"}' AND c.codigo='2.1.2.01';

-- Cartão -> Cartões a Receber
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='1.1.2.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.condicoes='{"contaLiquidacao":"1.1.2.02"}' AND c.codigo='2.1.2.01';

-- Fallback (sem forma) -> Caixa
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.prioridade=10 AND r.condicoes IS NULL AND c.codigo='1.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.adiantamento' AND r.prioridade=10 AND r.condicoes IS NULL AND c.codigo='2.1.2.01';

-- adiantamento.baixado: na conclusão, o adiantamento vira baixa do recebível.
-- D 2.1.2.01 Adiantamento / C 1.1.2.01 Clientes a Receber.
INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('adiantamento.baixado', 10, NULL, 'Baixa de adiantamento na conclusao {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='adiantamento.baixado' AND r.prioridade=10 AND c.codigo='2.1.2.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='adiantamento.baixado' AND r.prioridade=10 AND c.codigo='1.1.2.01';
