CREATE TABLE emails (
    id              BIGSERIAL PRIMARY KEY,
    destinatario    VARCHAR(255) NOT NULL,
    cc              VARCHAR(500),
    assunto         VARCHAR(500) NOT NULL,
    corpo           TEXT NOT NULL,
    html            BOOLEAN DEFAULT FALSE,
    template        VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'pendente',
    tentativas      INTEGER DEFAULT 0,
    erro_mensagem   TEXT,
    origem          VARCHAR(100),
    referencia_id   VARCHAR(100),
    agendado_para   TIMESTAMP,
    enviado_em      TIMESTAMP,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_emails_status ON emails(status);
CREATE INDEX idx_emails_origem ON emails(origem);
CREATE INDEX idx_emails_referencia ON emails(referencia_id);
CREATE INDEX idx_emails_agendado ON emails(agendado_para);
