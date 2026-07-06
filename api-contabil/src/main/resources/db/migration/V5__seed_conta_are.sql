-- V5: conta transitória de Apuração do Resultado do Exercício (ARE).
-- Usada só no encerramento anual: recebe o saldo das contas de resultado (receitas/custos/
-- despesas) e termina o exercício zerada, transferindo o lucro/prejuízo para o PL
-- (2.3.3.01 Lucros Acumulados / 2.3.3.02 (-) Prejuízos Acumulados, já semeadas na V2).
-- grupo 'pl' para NÃO entrar na DRE (é transitória, não é receita/despesa).

INSERT INTO contabil.conta_contabil (codigo, nome, tipo, natureza, grupo, retificadora, nivel, aceita_lancamento, ativo)
VALUES ('3.9.9.01', 'Apuracao do Resultado do Exercicio (ARE)', 'analitica', 'C', 'pl', FALSE, 4, TRUE, TRUE);
