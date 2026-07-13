-- V13: cofre das credenciais Meta -- prepara config_meta para os secrets CIFRADOS em repouso (LGPD).
--
-- Migration NOVA (nao editar V1..V12): Flyway incremental; o cliente instalado aplica no
-- boot/update. ALTER COLUMN ... SET DATA TYPE passa no Hibernate validate depois do Flyway.
--
-- A partir daqui, access_token/app_secret/verify_token sao gravados como
-- "v1:" + base64(AES-256-GCM) pelo CampoCifradoConverter (JPA @Convert). O texto cifrado e
-- maior que o plano (IV 12B + tag 16B + base64), entao as 3 colunas sao alargadas com folga.
-- phone_number_id NAO e secret e permanece VARCHAR(64).
--
-- SEM DOWNTIME: a leitura tolera texto plano legado (valor sem prefixo "v1:" volta como esta);
-- cada escrita passa a cifrar. Na config_meta instalada nao ha dado a migrar (0 linhas hoje —
-- os secrets moram no XML do servico como seed de env var).
--
-- PORTABILIDADE (mesma disciplina da V1/V6): "SET DATA TYPE VARCHAR(n)" e suportado por
-- PostgreSQL 15 E H2 2.x modo PostgreSQL. Evita-se "TEXT" (semantica diverge entre os dois).
-- Alargar VARCHAR (1024/255 -> 4096) preserva o dado existente (nao trunca).

ALTER TABLE whatsapp.config_meta ALTER COLUMN access_token SET DATA TYPE VARCHAR(4096);
ALTER TABLE whatsapp.config_meta ALTER COLUMN app_secret   SET DATA TYPE VARCHAR(4096);
ALTER TABLE whatsapp.config_meta ALTER COLUMN verify_token SET DATA TYPE VARCHAR(4096);
