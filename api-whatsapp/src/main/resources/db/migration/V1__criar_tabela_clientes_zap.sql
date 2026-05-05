-- V1: schema whatsapp + tabela de clientes WhatsApp (PER-02)
--
-- IDEMPOTENCIA: o instalador Inno Setup tambem cria o schema em prod via
-- 'CREATE SCHEMA IF NOT EXISTS whatsapp' antes do boot do api-whatsapp; em
-- test/dev a propria migration cria. Os dois caminhos sao seguros.
--
-- PORTABILIDADE (D-06 do CONTEXT.md):
--   * BIGINT GENERATED ALWAYS AS IDENTITY (SQL ANSI standard) e suportado
--     por PostgreSQL 10+ E H2 2.x — evita BIGSERIAL (PG-only) e
--     AUTO_INCREMENT (H2-only / MySQL-like).
--   * NOW() e TIMESTAMP DEFAULT NOW() funcionam em ambos.
--   * Empiricamente validado em H2 2.3.232 modo PostgreSQL antes de comprometer
--     com 4 migrations (spike STEP 0 do PLAN-04).

CREATE SCHEMA IF NOT EXISTS whatsapp;

CREATE TABLE whatsapp.clientes_zap (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_cliente_erp      BIGINT,
    telefone            VARCHAR(20) NOT NULL UNIQUE,
    ultima_mensagem_em  TIMESTAMP,
    criado_em           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clientes_zap_telefone ON whatsapp.clientes_zap(telefone);
CREATE INDEX idx_clientes_zap_id_cliente_erp ON whatsapp.clientes_zap(id_cliente_erp);
