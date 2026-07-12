-- V11: config_assistente -- persona do assistente virtual (WhatsApp), editavel em RUNTIME.
--
-- MOTIVACAO: tratar a integracao como um ASSISTENTE VIRTUAL com identidade propria
-- (nome, tom, emoji) e mensagens genericas de canal (saudacao, "nao entendi",
-- "fora do horario"). E CONFIG GENERICA -- NAO cita dominio de nenhum ERP -- entao
-- mora aqui no modulo e e reusavel entre ERPs (Odonto/Calhas/Mudas). O ERP consome
-- a persona pelo bloco "assistente" do callback e renderiza as mensagens de dominio.
--
-- SINGLETON: linha unica (id = 1), mesmo padrao do config_meta (V6). Ao contrario do
-- config_meta, aqui HA seed de defaults genericos (sem secret) para o bot ter
-- personalidade mesmo sem o operador configurar nada.
--
-- PORTABILIDADE (mesma disciplina da V1/V6): tipos e constraints suportados por
-- PostgreSQL 15 E H2 2.x modo PostgreSQL; constraints nomeadas em nivel de tabela.

CREATE TABLE whatsapp.config_assistente (
    id                     INTEGER NOT NULL,
    nome                   VARCHAR(60),
    emoji                  VARCHAR(16),
    tom                    VARCHAR(16)  NOT NULL DEFAULT 'informal',
    saudacao               VARCHAR(1000),
    mensagem_nao_entendi   VARCHAR(1000),
    mensagem_fora_horario  VARCHAR(1000),
    horario_inicio         VARCHAR(5),
    horario_fim            VARCHAR(5),
    dias_atendimento       VARCHAR(32),
    atualizado_em          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_config_assistente PRIMARY KEY (id),
    CONSTRAINT ck_config_assistente_singleton CHECK (id = 1)
);

-- Seed de defaults genericos (sem citar dominio). dias_atendimento em ISO-8601
-- (1=segunda ... 7=domingo); horarios em HH:mm.
INSERT INTO whatsapp.config_assistente
    (id, nome, emoji, tom, saudacao, mensagem_nao_entendi, mensagem_fora_horario,
     horario_inicio, horario_fim, dias_atendimento)
VALUES
    (1, 'Assistente', '', 'informal',
     'Olá! Como posso ajudar você hoje?',
     'Desculpe, não entendi. Vou te mostrar as opções pra facilitar.',
     'No momento estamos fora do horário de atendimento. Assim que possível retornaremos por aqui.',
     '08:00', '18:00', '1,2,3,4,5');
