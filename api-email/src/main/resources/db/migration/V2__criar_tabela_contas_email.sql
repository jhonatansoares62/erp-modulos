CREATE TABLE contas_email (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(100) NOT NULL,
    host            VARCHAR(255) NOT NULL,
    porta           INTEGER NOT NULL DEFAULT 587,
    username        VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    remetente       VARCHAR(255) NOT NULL,
    tls             BOOLEAN DEFAULT TRUE,
    padrao          BOOLEAN DEFAULT FALSE,
    ativo           BOOLEAN DEFAULT TRUE,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMP DEFAULT NOW()
);

ALTER TABLE emails ADD COLUMN conta_id BIGINT REFERENCES contas_email(id);
CREATE INDEX idx_emails_conta ON emails(conta_id);
