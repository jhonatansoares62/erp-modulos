-- V1: schema do módulo contábil (Fase 0 scaffold).
-- SQL portável: roda idêntico em H2 (MODE=PostgreSQL, testes) e PostgreSQL real.
-- Colunas JSON (payload, condicoes) como TEXT por portabilidade; migrar para jsonb
-- no PostgreSQL fica para a Fase 1 se precisar de query no JSON.
-- Valores monetários em centavos (BIGINT). Status como VARCHAR (sem enum).

CREATE SCHEMA IF NOT EXISTS contabil;

-- Plano de contas (árvore sintética/analítica). Só analítica recebe lançamento.
CREATE TABLE contabil.conta_contabil (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo            VARCHAR(40)  NOT NULL,
    nome              VARCHAR(200) NOT NULL,
    tipo              VARCHAR(20)  NOT NULL,
    natureza          VARCHAR(1)   NOT NULL,
    grupo             VARCHAR(20)  NOT NULL,
    retificadora      BOOLEAN      NOT NULL DEFAULT FALSE,
    pai_id            BIGINT,
    nivel             INT          NOT NULL,
    aceita_lancamento BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conta_codigo   UNIQUE (codigo),
    CONSTRAINT fk_conta_pai      FOREIGN KEY (pai_id) REFERENCES contabil.conta_contabil (id),
    CONSTRAINT ck_conta_tipo     CHECK (tipo IN ('sintetica','analitica')),
    CONSTRAINT ck_conta_natureza CHECK (natureza IN ('D','C')),
    CONSTRAINT ck_conta_grupo    CHECK (grupo IN ('ativo','passivo','pl','receita','custo','despesa'))
);

-- Roteiro contábil: regra evento -> partidas. Versionada por vigência.
CREATE TABLE contabil.regra_lancamento (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_tipo        VARCHAR(60)  NOT NULL,
    prioridade         INT          NOT NULL DEFAULT 0,
    condicoes          TEXT,
    historico_template VARCHAR(300),
    vigencia_inicio    DATE,
    vigencia_fim       DATE,
    versao             INT          NOT NULL DEFAULT 1,
    ativo              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Linhas de partida de uma regra (D/C, conta constante ou variável).
CREATE TABLE contabil.regra_partida (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regra_id    BIGINT       NOT NULL,
    tipo        VARCHAR(1)   NOT NULL,
    ordem       INT          NOT NULL DEFAULT 0,
    conta_modo  VARCHAR(20)  NOT NULL,
    conta_id    BIGINT,
    conta_campo VARCHAR(120),
    base        VARCHAR(20)  NOT NULL DEFAULT 'valor_total',
    percentual  NUMERIC(7,4),
    CONSTRAINT fk_partida_regra FOREIGN KEY (regra_id) REFERENCES contabil.regra_lancamento (id),
    CONSTRAINT fk_partida_conta FOREIGN KEY (conta_id) REFERENCES contabil.conta_contabil (id),
    CONSTRAINT ck_rpartida_tipo CHECK (tipo IN ('D','C')),
    CONSTRAINT ck_rpartida_modo CHECK (conta_modo IN ('constante','variavel')),
    CONSTRAINT ck_rpartida_base CHECK (base IN ('valor_total','percentual'))
);

-- Evento recebido do ERP. id = eventoId (UUID do cliente) -> idempotência.
CREATE TABLE contabil.evento_recebido (
    id             UUID PRIMARY KEY,
    tipo           VARCHAR(60)  NOT NULL,
    origem         VARCHAR(60)  NOT NULL,
    empresa_id     VARCHAR(60),
    payload        TEXT,
    data_evento    DATE         NOT NULL,
    valor_centavos BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'pendente',
    lancamento_id  BIGINT,
    erro_mensagem  VARCHAR(500),
    recebido_em    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_em  TIMESTAMP,
    CONSTRAINT ck_evento_status CHECK (status IN ('pendente','processado','sem_regra','erro'))
);

-- Lançamento contábil (cabeçalho). Imutável após 'lancado'; correção via estorno.
CREATE TABLE contabil.lancamento (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero           BIGINT,
    data_competencia DATE         NOT NULL,
    historico        VARCHAR(300),
    origem_evento_id UUID,
    origem_documento VARCHAR(120),
    estorna_id       BIGINT,
    estornado_por_id BIGINT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'rascunho',
    lancado_em       TIMESTAMP,
    hash             VARCHAR(64),
    hash_anterior    VARCHAR(64),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lanc_evento   FOREIGN KEY (origem_evento_id) REFERENCES contabil.evento_recebido (id),
    CONSTRAINT fk_lanc_estorna  FOREIGN KEY (estorna_id)       REFERENCES contabil.lancamento (id),
    CONSTRAINT fk_lanc_estpor   FOREIGN KEY (estornado_por_id) REFERENCES contabil.lancamento (id),
    CONSTRAINT ck_lanc_status   CHECK (status IN ('rascunho','lancado','estornado'))
);

-- Partidas de um lançamento. Invariante (aplicação): sum(D) = sum(C) por lançamento.
CREATE TABLE contabil.partida (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lancamento_id  BIGINT      NOT NULL,
    conta_id       BIGINT      NOT NULL,
    tipo           VARCHAR(1)  NOT NULL,
    valor_centavos BIGINT      NOT NULL,
    ordem          INT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_partida_lanc  FOREIGN KEY (lancamento_id) REFERENCES contabil.lancamento (id),
    CONSTRAINT fk_partida_conta2 FOREIGN KEY (conta_id)     REFERENCES contabil.conta_contabil (id),
    CONSTRAINT ck_partida_tipo  CHECK (tipo IN ('D','C'))
);

-- Fechamento de período (lock date) e encerramento de exercício.
CREATE TABLE contabil.periodo_fechado (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id      VARCHAR(60),
    competencia     VARCHAR(7)  NOT NULL,
    tipo            VARCHAR(20) NOT NULL,
    data_fechamento TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fechado_por     VARCHAR(120),
    CONSTRAINT ck_periodo_tipo CHECK (tipo IN ('mensal','exercicio'))
);

-- Snapshot de saldo por conta/competência (otimização de leitura; opcional).
CREATE TABLE contabil.saldo_snapshot (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conta_id       BIGINT      NOT NULL,
    competencia    VARCHAR(7)  NOT NULL,
    saldo_centavos BIGINT      NOT NULL,
    natureza       VARCHAR(1)  NOT NULL,
    gerado_em      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_snapshot_conta FOREIGN KEY (conta_id) REFERENCES contabil.conta_contabil (id),
    CONSTRAINT ck_snapshot_nat   CHECK (natureza IN ('D','C'))
);

CREATE INDEX ix_conta_pai        ON contabil.conta_contabil (pai_id);
CREATE INDEX ix_regra_evento     ON contabil.regra_lancamento (evento_tipo);
CREATE INDEX ix_evento_status    ON contabil.evento_recebido (status);
CREATE INDEX ix_partida_lanc     ON contabil.partida (lancamento_id);
CREATE INDEX ix_partida_conta    ON contabil.partida (conta_id);
CREATE INDEX ix_lanc_competencia ON contabil.lancamento (data_competencia);
