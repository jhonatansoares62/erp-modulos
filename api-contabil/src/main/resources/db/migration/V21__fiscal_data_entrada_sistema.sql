-- Corte de migração fiscal: a partir desta competência a receita é escriturada (razão); antes dela,
-- entra a receita histórica informada (V22) no RBT12. Nulo = comportamento atual (tudo escriturado).
ALTER TABLE contabil.fiscal_config ADD COLUMN data_entrada_sistema DATE;
