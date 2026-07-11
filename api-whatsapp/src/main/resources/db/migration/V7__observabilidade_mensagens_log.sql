-- V7: observabilidade — captura de dados hoje descartados no fluxo do webhook.
--
-- Motivacao (ANALISE-OBSERVABILIDADE-CUSTO.md §12): o modulo parseia mas descarta
-- o status de entrega (sent/delivered/read/failed), o motivo de falha, o timestamp
-- real da Meta, a conversa/categoria/billable (do status webhook) e a intencao
-- extraida da mensagem. Estas colunas passam a persistir esses dados para relatorios
-- e integracao uniforme com todos os ERPs. TODAS nullable — aditivo puro, sem quebrar
-- rows historicas nem o custo-zero (nada aqui envia mensagem).
--
-- Preenchimento:
--   * entrada (direcao=in): evento_em (ts Meta), id_cliente_erp (quando resolve), comando (intencao)
--   * saida  (direcao=out): evento_em (envio); status/status_em/erro_*/conversation_id/
--                           categoria/billable/conversa_origem chegam via status webhook (UPDATE por wamid)
--
-- H2 (PostgreSQL mode) e PostgreSQL: um ADD COLUMN por linha (mesmo estilo da V5).

ALTER TABLE whatsapp.mensagens_log ADD COLUMN id_cliente_erp  BIGINT;
ALTER TABLE whatsapp.mensagens_log ADD COLUMN evento_em       TIMESTAMP;
ALTER TABLE whatsapp.mensagens_log ADD COLUMN status          VARCHAR(12);
ALTER TABLE whatsapp.mensagens_log ADD COLUMN status_em       TIMESTAMP;
ALTER TABLE whatsapp.mensagens_log ADD COLUMN erro_codigo     VARCHAR(20);
ALTER TABLE whatsapp.mensagens_log ADD COLUMN erro_titulo     VARCHAR(255);
ALTER TABLE whatsapp.mensagens_log ADD COLUMN conversation_id VARCHAR(80);
ALTER TABLE whatsapp.mensagens_log ADD COLUMN categoria       VARCHAR(30);
ALTER TABLE whatsapp.mensagens_log ADD COLUMN billable        BOOLEAN;
ALTER TABLE whatsapp.mensagens_log ADD COLUMN conversa_origem VARCHAR(30);
ALTER TABLE whatsapp.mensagens_log ADD COLUMN comando         VARCHAR(255);

-- Indices para os cortes de relatorio (tabela e por-cliente, volume baixo).
CREATE INDEX idx_mensagens_log_direcao_criado ON whatsapp.mensagens_log(direcao, criado_em);
CREATE INDEX idx_mensagens_log_id_cliente_erp ON whatsapp.mensagens_log(id_cliente_erp);
