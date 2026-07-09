-- V19: marca os lançamentos gerados pelo encerramento de exercício.
-- A DRE soma o MOVIMENTO do período (partidas de receita/custo/despesa). O encerramento posta
-- em 31/12 D receitas / C ARE (e C custos/despesas / D ARE), então, depois de encerrar, a DRE do
-- ano lê zero. A coluna 'tipo' separa esses lançamentos para a DRE poder excluí-los; balancete,
-- diário e balanço continuam somando tudo (o resultado migrou para o PL e o balanço fecha).

ALTER TABLE contabil.lancamento ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'normal';

-- Backfill: encerramentos já postados (identificados pelo histórico) viram tipo 'encerramento'.
UPDATE contabil.lancamento SET tipo = 'encerramento'
 WHERE historico LIKE 'Encerramento %' OR historico LIKE 'Apuracao do resultado %';

ALTER TABLE contabil.lancamento ADD CONSTRAINT ck_lanc_tipo CHECK (tipo IN ('normal','encerramento'));
