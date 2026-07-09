-- V23: novo status 'periodo_fechado' para evento_recebido. Um evento que casou um roteiro mas caiu
-- em período fechado (mês fechado ou exercício encerrado) NÃO estoura a aprovação do ERP: vira
-- pendência 'periodo_fechado' (evento preservado com a mensagem), para o contador reabrir/ajustar e
-- reprocessar. Só amplia o domínio do CHECK (adiciona valor) — nenhuma linha existente viola.
-- Portável H2 (dev) / PostgreSQL (teste, homolog): DROP/ADD CONSTRAINT com nome explícito da V1.
ALTER TABLE contabil.evento_recebido DROP CONSTRAINT ck_evento_status;
ALTER TABLE contabil.evento_recebido ADD CONSTRAINT ck_evento_status
    CHECK (status IN ('pendente','processado','sem_regra','erro','periodo_fechado'));
