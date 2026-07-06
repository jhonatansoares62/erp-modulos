-- V4: roteiro de despesa avulsa (para DRE ter linha de despesa).
-- despesa.incorrida: D Despesas administrativas (3.2.2.01) · C Caixa (1.1.1.01).

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('despesa.incorrida', 10, NULL, 'Despesa {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=10 AND c.codigo='3.2.2.01';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='despesa.incorrida' AND r.prioridade=10 AND c.codigo='1.1.1.01';
