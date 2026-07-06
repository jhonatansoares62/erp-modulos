-- V7: apuração de CMV por inventário periódico (contagem física).
-- Registro de auditoria de cada apuração: EI + Compras - EF = CMV, ligada ao lançamento
-- D 3.2.1.01 CMV / C 1.1.3.01 Estoque. Reapurar o mesmo período desativa (ativo=false) a
-- apuração anterior e estorna o lançamento; a vigente é a linha ativo=true.
CREATE TABLE contabil.inventario_apuracao (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    periodo_de               DATE      NOT NULL,
    periodo_ate              DATE      NOT NULL,
    estoque_inicial_centavos BIGINT    NOT NULL,
    compras_centavos         BIGINT    NOT NULL,
    estoque_final_centavos   BIGINT    NOT NULL,
    cmv_centavos             BIGINT    NOT NULL,
    lancamento_id            BIGINT,
    apurado_por              VARCHAR(120),
    apurado_em               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ativo                    BOOLEAN   NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_inv_lanc FOREIGN KEY (lancamento_id) REFERENCES contabil.lancamento (id)
);

CREATE INDEX idx_inv_periodo ON contabil.inventario_apuracao (periodo_de, periodo_ate, ativo);
