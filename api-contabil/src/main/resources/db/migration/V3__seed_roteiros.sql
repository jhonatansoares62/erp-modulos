-- V3: seed de roteiros contábeis de exemplo (MVP), referenciando o plano de contas da V2.
-- Demonstra o padrão evento -> partidas com first-match por prioridade (mais específica
-- vence) e conta constante. INSERT ... SELECT para resolver regra/conta por chave natural
-- (portável entre H2 e PostgreSQL, sem subquery em VALUES).

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('venda.finalizada',    30, '{"condicao":"avista","meioPagamento":"pix"}', 'Venda {numero} recebida via PIX', 1, TRUE),
 ('venda.finalizada',    20, '{"condicao":"avista"}',                        'Venda {numero} a vista',         1, TRUE),
 ('venda.finalizada',    10, '{"condicao":"prazo"}',                         'Venda {numero} a prazo',         1, TRUE),
 ('recebimento.baixado', 10, NULL,                                           'Recebimento do titulo {numero}', 1, TRUE),
 ('compra.recebida',     10, '{"categoria":"insumo"}',                       'Compra de insumos {numero}',     1, TRUE),
 ('pagamento.efetuado',  10, NULL,                                           'Pagamento {numero}',             1, TRUE);

-- venda.finalizada à vista via PIX (prioridade 30): D Bancos/PIX · C Receita
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.finalizada' AND r.prioridade=30 AND c.codigo='1.1.1.02';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.finalizada' AND r.prioridade=30 AND c.codigo='3.1.1.01';

-- venda.finalizada à vista (prioridade 20): D Caixa · C Receita
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.finalizada' AND r.prioridade=20 AND c.codigo='1.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.finalizada' AND r.prioridade=20 AND c.codigo='3.1.1.01';

-- venda.finalizada a prazo (prioridade 10): D Clientes a Receber · C Receita
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.finalizada' AND r.prioridade=10 AND c.codigo='1.1.2.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='venda.finalizada' AND r.prioridade=10 AND c.codigo='3.1.1.01';

-- recebimento.baixado: D Caixa · C Clientes a Receber
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=10 AND c.codigo='1.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='recebimento.baixado' AND r.prioridade=10 AND c.codigo='1.1.2.01';

-- compra.recebida (insumo): D Estoque · C Fornecedores a Pagar
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='compra.recebida' AND r.prioridade=10 AND c.codigo='1.1.3.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='compra.recebida' AND r.prioridade=10 AND c.codigo='2.1.1.01';

-- pagamento.efetuado: D Fornecedores a Pagar · C Caixa
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=10 AND c.codigo='2.1.1.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='pagamento.efetuado' AND r.prioridade=10 AND c.codigo='1.1.1.01';
