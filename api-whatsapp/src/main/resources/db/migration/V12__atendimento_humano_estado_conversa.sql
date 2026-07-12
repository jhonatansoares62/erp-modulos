-- V12: handoff (atendimento humano) -- estende a estado_conversa (V4, ate agora placeholder vazio).
--
-- Migration NOVA (nao editar V1..V11): Flyway incremental; o cliente instalado aplica no
-- boot/update. ALTER TABLE ADD COLUMN passa no Hibernate validate apos o Flyway aplicar.
--
-- Quando em_atendimento = true, o bot NAO responde aquele telefone (guard no
-- MensagemAsyncListener, antes do dispatch ao ERP): a recepcao assume/encerra pelo inbox
-- do whatsapp-app. Conceito GENERICO do canal -- reusavel entre ERPs (Odonto/Calhas/Mudas).

ALTER TABLE whatsapp.estado_conversa
    ADD COLUMN em_atendimento BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE whatsapp.estado_conversa
    ADD COLUMN iniciado_em TIMESTAMP;
