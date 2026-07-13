-- V14: auditoria_acesso -- trilha de acesso ao dado do paciente no inbox (LGPD item 3).
--
-- Migration NOVA (nao editar V1..V13): Flyway incremental; o cliente instalado aplica no
-- boot/update. CREATE TABLE passa no Hibernate validate depois do Flyway.
--
-- MOTIVACAO (LGPD Art. 37/46): accountability -- registrar QUEM (atendente) acessou o dado
-- de QUEM (telefone do paciente) e QUANDO. Fecha o "incidente de exposicao": o inbox ja e
-- gated por login (JWT), agora tambem deixa rastro. Um AuditoriaAcessoInterceptor grava as
-- acoes relevantes (abriu_chat / assumiu / encerrou); a lista de conversas (polling ~5s) NAO
-- e auditada (evita inundar), e abriu_chat tem dedup por (atendente, telefone) em janela.
--
-- atendente_email nullable: acesso via X-API-Key (ERP/sistema) nao tem atendente humano.
-- PORTABILIDADE (mesma disciplina da V2/V9): IDENTITY GENERATED ALWAYS, VARCHAR, TIMESTAMP
-- NOW() -- suportados por PostgreSQL 15 E H2 2.x modo PostgreSQL.

CREATE TABLE whatsapp.auditoria_acesso (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    atendente_email  VARCHAR(160),
    acao             VARCHAR(40) NOT NULL,
    telefone_alvo    VARCHAR(20),
    criado_em        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auditoria_acesso_criado_em ON whatsapp.auditoria_acesso(criado_em);
CREATE INDEX idx_auditoria_acesso_telefone ON whatsapp.auditoria_acesso(telefone_alvo);
