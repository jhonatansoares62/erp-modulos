-- Anexo opcional do e-mail (ex.: PDF de orcamento/recibo), conteudo em base64.
ALTER TABLE emails ADD COLUMN anexo_nome VARCHAR(255);
ALTER TABLE emails ADD COLUMN anexo_tipo VARCHAR(100);
ALTER TABLE emails ADD COLUMN anexo_base64 TEXT;
