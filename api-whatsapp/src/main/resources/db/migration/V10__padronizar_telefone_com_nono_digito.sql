-- V10 — Padroniza telefones de celular BR para 13 digitos (COM o 9o digito).
--
-- Contexto: versoes anteriores de TelefoneBR.normalizar() REMOVIAM o 9 de celulares
-- fora de SP/RJ/ES (regra ANATEL 2010), gravando 12 digitos (55 + DDD + 8). Na pratica
-- a Cloud API HOJE so ENTREGA para celular BR COM o 9 (erro 131026 sem ele) e o Meta
-- manda o wa_id entrante COM o 9. O canonico passou a ser SEMPRE com o 9. Esta migration
-- alinha os dados ja gravados a esse canonico.
--
-- Alvo: telefones de 12 digitos comecando com 55 (55 + DDD + 8 = celular BR sem o 9).
-- Excluidos: 13 digitos (ja com o 9), nao-BR (nao comeca com 55), outros comprimentos.
-- Assuncao: 12-digito BR = celular sem o 9 (WhatsApp e mobile; mesma premissa do codigo).
--
-- Tabelas com UNIQUE/PK em telefone (clientes_zap, estado_conversa): se ja existir o par
-- COM o 9, remove o registro SEM o 9 (o com-9 e a fonte da verdade) antes de inserir o 9,
-- evitando violar a constraint. mensagens_log (sem unique) so recebe o UPDATE.

-- ── clientes_zap ──
DELETE FROM whatsapp.clientes_zap
WHERE LENGTH(clientes_zap.telefone) = 12
  AND clientes_zap.telefone LIKE '55%'
  AND EXISTS (
      SELECT 1 FROM whatsapp.clientes_zap c2
      WHERE c2.telefone = SUBSTRING(clientes_zap.telefone, 1, 4) || '9'
                          || SUBSTRING(clientes_zap.telefone, 5)
  );

UPDATE whatsapp.clientes_zap
SET telefone = SUBSTRING(telefone, 1, 4) || '9' || SUBSTRING(telefone, 5)
WHERE LENGTH(telefone) = 12
  AND telefone LIKE '55%';

-- ── estado_conversa ──
DELETE FROM whatsapp.estado_conversa
WHERE LENGTH(estado_conversa.telefone) = 12
  AND estado_conversa.telefone LIKE '55%'
  AND EXISTS (
      SELECT 1 FROM whatsapp.estado_conversa e2
      WHERE e2.telefone = SUBSTRING(estado_conversa.telefone, 1, 4) || '9'
                          || SUBSTRING(estado_conversa.telefone, 5)
  );

UPDATE whatsapp.estado_conversa
SET telefone = SUBSTRING(telefone, 1, 4) || '9' || SUBSTRING(telefone, 5)
WHERE LENGTH(telefone) = 12
  AND telefone LIKE '55%';

-- ── mensagens_log (sem unique — so o UPDATE) ──
UPDATE whatsapp.mensagens_log
SET telefone = SUBSTRING(telefone, 1, 4) || '9' || SUBSTRING(telefone, 5)
WHERE LENGTH(telefone) = 12
  AND telefone LIKE '55%';
