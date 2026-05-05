-- V3: cache de media_id por sha256 do arquivo (PER-04)
--
-- TTL: gerenciado pela aplicacao (Phase 4 calcula now() + 30 dias e popula
-- expira_em explicitamente). Cache evita re-upload do mesmo arquivo para o
-- /media endpoint do Meta (custo zero — Meta nao cobra upload mas economiza
-- banda + latencia + chance de rate limit).
--
-- arquivo_hash CHAR(64): sha256 hex digest tem exatamente 64 caracteres.
-- CHAR(64) economiza vs VARCHAR(64) por ser fixed-width (PostgreSQL trata
-- iguais, mas a intencao fica explicita no schema).
--
-- Indice em expira_em: futuro batch de limpeza pode escanear linhas
-- expiradas eficientemente (Phase 4 ou Phase 6).

CREATE TABLE whatsapp.media_cache (
    arquivo_hash    CHAR(64) PRIMARY KEY,
    media_id        VARCHAR(255) NOT NULL,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW(),
    expira_em       TIMESTAMP NOT NULL
);

CREATE INDEX idx_media_cache_expira_em ON whatsapp.media_cache(expira_em);
