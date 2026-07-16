-- V1: schema do modulo de backup (db_api_backup, schema 'backup').
--
-- Historico de execucoes de backup: base para o grid da tela, status off-site por execucao,
-- verificacao (pg_restore --list) e catch-up no boot (ultimo backup bem-sucedido). Uma linha
-- por dump (por banco), gravada pelo BackupService. Espelha o V11 do ERP (backup in-JVM), mas
-- aqui vive no banco proprio do modulo.

CREATE TABLE backup_historico (
    id             BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(20)  NOT NULL,               -- diario | pre-update | restore-test
    banco          VARCHAR(100) NOT NULL,
    arquivo        VARCHAR(255),
    tamanho_bytes  BIGINT,
    status         VARCHAR(20)  NOT NULL,               -- ok | erro
    verificado     BOOLEAN      NOT NULL DEFAULT FALSE,
    offsite_status VARCHAR(20)  NOT NULL DEFAULT 'nao', -- nao | pendente | enviado | falha
    erro_msg       VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        BIGINT       DEFAULT 0
);

CREATE INDEX idx_backup_hist_created ON backup_historico (created_at DESC);
