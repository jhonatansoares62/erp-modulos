-- V6: config_meta -- credenciais Meta (WhatsApp Cloud API) editaveis em RUNTIME.
--
-- MOTIVACAO: ate a V5 as 4 credenciais Meta vinham SO de env var
-- (WHATSAPP_PHONE_NUMBER_ID/ACCESS_TOKEN/APP_SECRET/VERIFY_TOKEN), setadas no
-- install via XML do WinSW. Editar XML no cliente e ruim e o modulo NAO subia
-- sem elas (WhatsAppProperties @NotBlank). Agora o modulo sobe sem Meta e o ERP
-- grava as credenciais aqui via PUT /api/whatsapp/config; MetaConfigService le no
-- boot e sobrepoe no WhatsAppProperties (bean vivo). Env var continua como seed.
--
-- SINGLETON: linha unica (id = 1). O CHECK impede uma segunda linha; o upsert do
-- service sempre usa id = 1. Sem seed -- ausencia da linha = nao configurado
-- (modulo sobe, mas envio/webhook so funcionam apos o operador salvar).
--
-- PORTABILIDADE (mesma disciplina da V1): tipos e constraints suportados por
-- PostgreSQL 15 E H2 2.x modo PostgreSQL. Constraints em nivel de TABELA (nomeadas)
-- para evitar ambiguidade de ordem inline entre H2 e PG. access_token com folga
-- (tokens permanentes do Meta passam de 200 chars). NOW() e TIMESTAMP em ambos.

CREATE TABLE whatsapp.config_meta (
    id               INTEGER NOT NULL,
    phone_number_id  VARCHAR(64),
    access_token     VARCHAR(1024),
    app_secret       VARCHAR(255),
    verify_token     VARCHAR(255),
    atualizado_em    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_config_meta PRIMARY KEY (id),
    CONSTRAINT ck_config_meta_singleton CHECK (id = 1)
);
