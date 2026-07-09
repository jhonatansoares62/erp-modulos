-- Receita bruta histórica (PARÂMETRO FISCAL, não é lançamento contábil) dos 12 meses anteriores ao
-- corte, para o RBT12 de empresa migrando cair na faixa/alíquota correta antes de qualquer venda
-- escriturada. Single-tenant, como fiscal_config.
CREATE TABLE contabil.fiscal_receita_historica (
    competencia            VARCHAR(7) PRIMARY KEY,   -- 'YYYY-MM'
    receita_bruta_centavos BIGINT NOT NULL
);
