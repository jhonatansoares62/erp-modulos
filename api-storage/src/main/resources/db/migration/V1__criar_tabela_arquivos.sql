CREATE TABLE arquivos (
    id                BIGSERIAL PRIMARY KEY,
    nome_original     VARCHAR(500) NOT NULL,
    nome_armazenado   VARCHAR(500) NOT NULL UNIQUE,
    content_type      VARCHAR(100) NOT NULL,
    tamanho           BIGINT NOT NULL,
    categoria         VARCHAR(50),
    origem            VARCHAR(100),
    referencia_id     VARCHAR(100),
    tem_thumbnail     BOOLEAN DEFAULT FALSE,
    ativo             BOOLEAN DEFAULT TRUE,
    criado_em         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_arquivos_categoria ON arquivos(categoria);
CREATE INDEX idx_arquivos_origem ON arquivos(origem);
CREATE INDEX idx_arquivos_referencia ON arquivos(referencia_id);
CREATE INDEX idx_arquivos_ativo ON arquivos(ativo);
