-- V4: placeholder minimo para estado de conversa (D6 do PROJECT.md)
--
-- ESCOPO PHASE 1: criar so a estrutura minima — telefone como PK +
-- ultima_atualizacao com default NOW(). Suficiente para:
--   * Hibernate ddl-auto: validate nao reclamar (mesmo sem @Entity em Phase 1)
--   * ROADMAP success criterion 5 (4 migrations aplicadas)
--   * Phases futuras estenderem via ALTER TABLE (V5+) com colunas adicionais
--     como estado VARCHAR(50), ultimo_comando VARCHAR(100), contexto JSON, etc.
--
-- D6 do PROJECT.md: "Persistencia minima de estado de conversa — so
-- ultima_mensagem_em por enquanto." Phase 2+ pode expandir conforme necessidade
-- do roteamento de comandos / fluxos de conversa.

CREATE TABLE whatsapp.estado_conversa (
    telefone            VARCHAR(20) PRIMARY KEY,
    ultima_atualizacao  TIMESTAMP NOT NULL DEFAULT NOW()
);
