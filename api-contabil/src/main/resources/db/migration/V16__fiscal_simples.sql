-- V16: pacote FISCAL dentro da api-contabil — cálculo automático da alíquota do Simples.
-- Tabela de faixas versionável (lookup, não hardcoded) + config fiscal (single-row). Sem NF/SPED.
-- Anexos III e V (serviços, Odonto) e I e II (comércio/indústria, Calhas/Mudas) já previstos.

-- Faixas do Simples: por anexo, limite de RBT12, alíquota nominal e parcela a deduzir.
CREATE TABLE contabil.fiscal_faixa_simples (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anexo                    VARCHAR(4)   NOT NULL,            -- I, II, III, V
    faixa                    INT          NOT NULL,            -- 1..6
    rbt12_ate_centavos       BIGINT       NOT NULL,            -- limite superior de RBT12 da faixa
    aliquota_nominal         NUMERIC(6,4) NOT NULL,            -- % (ex.: 11.2000)
    parcela_deduzir_centavos BIGINT       NOT NULL,
    vigencia_inicio          DATE         NOT NULL DEFAULT DATE '2018-01-01',
    CONSTRAINT uk_faixa_simples UNIQUE (anexo, faixa, vigencia_inicio)
);

-- Anexo I (comércio) — previsto p/ Calhas/Mudas.
INSERT INTO contabil.fiscal_faixa_simples (anexo, faixa, rbt12_ate_centavos, aliquota_nominal, parcela_deduzir_centavos) VALUES
 ('I', 1,  18000000,  4.0000,        0),
 ('I', 2,  36000000,  7.3000,   594000),
 ('I', 3,  72000000,  9.5000,  1386000),
 ('I', 4, 180000000, 10.7000,  2250000),
 ('I', 5, 360000000, 14.3000,  8730000),
 ('I', 6, 480000000, 19.0000, 37800000);

-- Anexo II (indústria).
INSERT INTO contabil.fiscal_faixa_simples (anexo, faixa, rbt12_ate_centavos, aliquota_nominal, parcela_deduzir_centavos) VALUES
 ('II', 1,  18000000,  4.5000,        0),
 ('II', 2,  36000000,  7.8000,   594000),
 ('II', 3,  72000000, 10.0000,  1386000),
 ('II', 4, 180000000, 11.2000,  2250000),
 ('II', 5, 360000000, 14.7000,  8550000),
 ('II', 6, 480000000, 30.0000, 72000000);

-- Anexo III (serviços — Odonto quando Fator R >= 28%).
INSERT INTO contabil.fiscal_faixa_simples (anexo, faixa, rbt12_ate_centavos, aliquota_nominal, parcela_deduzir_centavos) VALUES
 ('III', 1,  18000000,  6.0000,        0),
 ('III', 2,  36000000, 11.2000,   936000),
 ('III', 3,  72000000, 13.5000,  1764000),
 ('III', 4, 180000000, 16.0000,  3564000),
 ('III', 5, 360000000, 21.0000, 12564000),
 ('III', 6, 480000000, 33.0000, 64800000);

-- Anexo V (serviços — Odonto quando Fator R < 28%).
INSERT INTO contabil.fiscal_faixa_simples (anexo, faixa, rbt12_ate_centavos, aliquota_nominal, parcela_deduzir_centavos) VALUES
 ('V', 1,  18000000, 15.5000,        0),
 ('V', 2,  36000000, 18.0000,   450000),
 ('V', 3,  72000000, 19.5000,   990000),
 ('V', 4, 180000000, 20.5000,  1710000),
 ('V', 5, 360000000, 23.0000,  6210000),
 ('V', 6, 480000000, 30.5000, 54000000);

-- Config fiscal (single-row). anexo: I|II|III|V|AUTO (AUTO = III/V por Fator R).
CREATE TABLE contabil.fiscal_config (
    id                    INT          PRIMARY KEY DEFAULT 1,
    regime                VARCHAR(30)  NOT NULL DEFAULT 'simples_nacional',
    calculo_automatico    BOOLEAN      NOT NULL DEFAULT FALSE,
    anexo                 VARCHAR(4)   NOT NULL DEFAULT 'AUTO',
    data_inicio_atividade DATE,
    folha12m_centavos     BIGINT       NOT NULL DEFAULT 0,
    atualizado_em         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_fiscal_config_singleton CHECK (id = 1)
);

INSERT INTO contabil.fiscal_config (id) VALUES (1);
