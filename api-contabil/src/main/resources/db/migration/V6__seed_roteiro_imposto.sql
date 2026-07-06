-- V6: roteiro de apuração de imposto sobre a venda (Simples Nacional).
-- imposto.apurado: D (-) Deduções de Tributos/Devoluções · C Simples Nacional a Recolher.
-- O valor (receita × alíquota) é calculado no ERP, dono do parâmetro fiscal, e chega em
-- valor_total; a Contabilidade só lança. Mesmo padrão da V3 (INSERT ... SELECT por código).

INSERT INTO contabil.regra_lancamento (evento_tipo, prioridade, condicoes, historico_template, versao, ativo) VALUES
 ('imposto.apurado', 10, NULL, 'Simples Nacional s/ venda {numero}', 1, TRUE);

INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'D', 0, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='imposto.apurado' AND r.prioridade=10 AND c.codigo='3.1.1.04';
INSERT INTO contabil.regra_partida (regra_id, tipo, ordem, conta_modo, conta_id, base)
SELECT r.id, 'C', 1, 'constante', c.id, 'valor_total' FROM contabil.regra_lancamento r, contabil.conta_contabil c
 WHERE r.evento_tipo='imposto.apurado' AND r.prioridade=10 AND c.codigo='2.1.3.01';
