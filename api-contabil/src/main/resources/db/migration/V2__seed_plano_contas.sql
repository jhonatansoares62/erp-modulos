-- V2: seed do plano de contas — modelo simplificado ITG 1000 (Anexo 4).
-- Sugerido (não obrigatório): o contador ajusta depois. Grupos 1/2/3 (sem grupo 4),
-- PL em 2.3, resultado no grupo único 3 (3.1 Receitas / 3.2 Custos e Despesas).
-- pai_id fica NULL: a árvore é derivada do código no serviço (parent = código sem o
-- último segmento). Retificadoras (-) invertem a natureza do grupo.
-- Só contas analíticas (nível 4) recebem lançamento (aceita_lancamento = TRUE).

INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, nivel, aceita_lancamento, ativo) VALUES
 ('1',        'Ativo',                              'sintetica', 'D', 'ativo',   FALSE, 1, FALSE, TRUE),
 ('1.1',      'Ativo Circulante',                   'sintetica', 'D', 'ativo',   FALSE, 2, FALSE, TRUE),
 ('1.1.1',    'Caixa e Equivalentes de Caixa',      'sintetica', 'D', 'ativo',   FALSE, 3, FALSE, TRUE),
 ('1.1.1.01', 'Caixa',                              'analitica', 'D', 'ativo',   FALSE, 4, TRUE,  TRUE),
 ('1.1.1.02', 'Bancos Conta Movimento / PIX',       'analitica', 'D', 'ativo',   FALSE, 4, TRUE,  TRUE),
 ('1.1.2',    'Contas a Receber',                   'sintetica', 'D', 'ativo',   FALSE, 3, FALSE, TRUE),
 ('1.1.2.01', 'Clientes a Receber',                 'analitica', 'D', 'ativo',   FALSE, 4, TRUE,  TRUE),
 ('1.1.3',    'Estoques',                           'sintetica', 'D', 'ativo',   FALSE, 3, FALSE, TRUE),
 ('1.1.3.01', 'Estoque de mercadorias/insumos',     'analitica', 'D', 'ativo',   FALSE, 4, TRUE,  TRUE),
 ('2',        'Passivo e Patrimonio Liquido',       'sintetica', 'C', 'passivo', FALSE, 1, FALSE, TRUE),
 ('2.1',      'Passivo Circulante',                 'sintetica', 'C', 'passivo', FALSE, 2, FALSE, TRUE),
 ('2.1.1',    'Fornecedores',                       'sintetica', 'C', 'passivo', FALSE, 3, FALSE, TRUE),
 ('2.1.1.01', 'Fornecedores a Pagar',               'analitica', 'C', 'passivo', FALSE, 4, TRUE,  TRUE),
 ('2.1.3',    'Obrigacoes Fiscais',                 'sintetica', 'C', 'passivo', FALSE, 3, FALSE, TRUE),
 ('2.1.3.01', 'Simples Nacional a Recolher',        'analitica', 'C', 'passivo', FALSE, 4, TRUE,  TRUE),
 ('2.3',      'Patrimonio Liquido',                 'sintetica', 'C', 'pl',      FALSE, 2, FALSE, TRUE),
 ('2.3.1',    'Capital Social',                     'sintetica', 'C', 'pl',      FALSE, 3, FALSE, TRUE),
 ('2.3.1.01', 'Capital Social',                     'analitica', 'C', 'pl',      FALSE, 4, TRUE,  TRUE),
 ('2.3.3',    'Lucros ou Prejuizos Acumulados',     'sintetica', 'C', 'pl',      FALSE, 3, FALSE, TRUE),
 ('2.3.3.01', 'Lucros Acumulados',                  'analitica', 'C', 'pl',      FALSE, 4, TRUE,  TRUE),
 ('2.3.3.02', '(-) Prejuizos Acumulados',           'analitica', 'D', 'pl',      TRUE,  4, TRUE,  TRUE),
 ('3',        'Receitas, Custos e Despesas',        'sintetica', 'C', 'receita', FALSE, 1, FALSE, TRUE),
 ('3.1',      'Receitas',                           'sintetica', 'C', 'receita', FALSE, 2, FALSE, TRUE),
 ('3.1.1',    'Receitas de Venda',                  'sintetica', 'C', 'receita', FALSE, 3, FALSE, TRUE),
 ('3.1.1.01', 'Receita de vendas',                  'analitica', 'C', 'receita', FALSE, 4, TRUE,  TRUE),
 ('3.1.1.04', '(-) Deducoes de Tributos/Devolucoes','analitica', 'D', 'receita', TRUE,  4, TRUE,  TRUE),
 ('3.2',      'Custos e Despesas',                  'sintetica', 'D', 'despesa', FALSE, 2, FALSE, TRUE),
 ('3.2.1',    'Custo das Vendas',                   'sintetica', 'D', 'custo',   FALSE, 3, FALSE, TRUE),
 ('3.2.1.01', 'Custo das mercadorias/insumos',      'analitica', 'D', 'custo',   FALSE, 4, TRUE,  TRUE),
 ('3.2.2',    'Despesas Operacionais',              'sintetica', 'D', 'despesa', FALSE, 3, FALSE, TRUE),
 ('3.2.2.01', 'Despesas administrativas',           'analitica', 'D', 'despesa', FALSE, 4, TRUE,  TRUE),
 ('3.2.2.02', 'Despesas com vendas',                'analitica', 'D', 'despesa', FALSE, 4, TRUE,  TRUE),
 ('3.2.3',    'Despesas Financeiras',               'sintetica', 'D', 'despesa', FALSE, 3, FALSE, TRUE),
 ('3.2.3.01', 'Despesas financeiras',               'analitica', 'D', 'despesa', FALSE, 4, TRUE,  TRUE);
