-- V8: desfecho do bot (§12 #6). O ERP passa a devolver no callback se a mensagem
-- casou um handler e o resultado do processamento; antes o corpo da resposta do ERP
-- era descartado (ErpCallbackClient.toBodilessEntity). Persistir isso permite medir
-- efetividade do bot (taxa de "nao entendi" / silencio / erro).
--
-- Valores (so linhas de ENTRADA): respondido | nao_entendi | sem_resposta | erro.
-- Nullable/aditivo — custo-zero preservado.

ALTER TABLE whatsapp.mensagens_log ADD COLUMN resultado VARCHAR(20);
