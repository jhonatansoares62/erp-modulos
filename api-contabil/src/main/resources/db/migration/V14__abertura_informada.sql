-- V14: saldos de abertura INFORMADOS pelo usuário (onboarding real).
-- Quando a clínica informa os saldos iniciais reais (Caixa, Bancos, Capital, ...), esta
-- tabela guarda o conjunto informado (uma linha por conta) e a data de abertura. O
-- AberturaService reaplica esse conjunto como lançamento de abertura (origem_documento
-- 'abertura-informada') e, havendo informados, NÃO usa o auto-aporte de capital (fallback).
-- Conjunto único (single-tenant por instância do módulo): informar substitui tudo.

CREATE TABLE contabil.abertura_informada (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data_abertura  DATE         NOT NULL,
    conta_codigo   VARCHAR(20)  NOT NULL,
    saldo_centavos BIGINT       NOT NULL,
    informado_por  VARCHAR(120),
    informado_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_abertura_informada_conta UNIQUE (conta_codigo)
);
