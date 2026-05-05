-- V2: log de mensagens recebidas e enviadas (PER-03)
--
-- IDEMPOTENCIA / GATE: 'wamid VARCHAR(255) NOT NULL UNIQUE' e o gate atomico
-- para evitar processamento duplicado quando Meta reenviar webhook (Phase 2,
-- WEB-05/WEB-06). INSERT com wamid duplicado falha com violacao de UNIQUE,
-- e a aplicacao trata como sinal de "ja processado, ignorar".
--
-- CHECK direcao: empiricamente confirmado que H2 2.3.232 modo PostgreSQL NAO
-- silencia CHECK constraint (spike STEP 0). Insert com 'xx' dispara
-- JdbcSQLIntegrityConstraintViolationException.
--
-- INDICES:
--   * telefone — buscar historico de mensagens por cliente (Phase 2)
--   * criado_em — listagem cronologica + futuras consultas de janela 24h
--   * wamid ja tem UNIQUE index implicito da constraint NOT NULL UNIQUE.

CREATE TABLE whatsapp.mensagens_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wamid       VARCHAR(255) NOT NULL UNIQUE,
    telefone    VARCHAR(20) NOT NULL,
    direcao     VARCHAR(3) NOT NULL,
    tipo        VARCHAR(50),
    conteudo    TEXT,
    media_id    VARCHAR(255),
    criado_em   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mensagens_log_direcao CHECK (direcao IN ('in', 'out'))
);

CREATE INDEX idx_mensagens_log_telefone ON whatsapp.mensagens_log(telefone);
CREATE INDEX idx_mensagens_log_criado_em ON whatsapp.mensagens_log(criado_em);
