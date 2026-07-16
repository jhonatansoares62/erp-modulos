package br.com.erpkit.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

/**
 * Cifra de arquivos de backup em repouso (AES-256-GCM). A chave (256 bits, Base64) mora FORA
 * do banco, num arquivo .properties em {@code C:\erpkit\config\<erp>\backup.properties}
 * (propriedade {@code backup.enc-key}, auto-gera no 1o uso). SEM escrow: o cliente e
 * responsavel por guardar esse arquivo fora da maquina — sem ele, o backup cifrado e
 * irrecuperavel (um pg_dump sozinho nao decifra).
 *
 * <p>Formato do arquivo cifrado: [MAGIC(5) | IV(12) | ciphertext+tag]. O MAGIC {@code ODBK1}
 * e mantido identico ao do backup in-JVM do ERP para que este modulo consiga decifrar os
 * {@code .enc} ja gerados (compatibilidade no cutover/dual-run).
 *
 * <p>Nota de escala: opera com o arquivo inteiro em memoria (readAllBytes/doFinal), adequado
 * ao tamanho esperado (poucos MB, e o dump ja e -Fc comprimido). Se crescer para GB, migrar
 * para framing em blocos (GCM por chunk) ou CTR+HMAC.
 */
@Component
public class BackupCripto {

    private static final Logger log = LoggerFactory.getLogger(BackupCripto.class);

    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] MAGIC = {'O', 'D', 'B', 'K', '1'};
    private static final String PROP_CHAVE = "backup.enc-key";

    private final String keyFilePath;
    private final SecureRandom rng = new SecureRandom();
    private volatile SecretKeySpec chave;

    public BackupCripto(
            @Value("${app.backup.enc-key-file:C:/erpkit/config/erpodonto/backup.properties}") String keyFilePath) {
        this.keyFilePath = keyFilePath;
    }

    public void cifrarArquivo(File entrada, File saida) throws IOException {
        try {
            byte[] plano = Files.readAllBytes(entrada.toPath());
            byte[] iv = new byte[IV_BYTES];
            rng.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plano);

            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(saida))) {
                out.write(MAGIC);
                out.write(iv);
                out.write(ct);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Falha ao cifrar backup: " + e.getMessage(), e);
        }
    }

    public void decifrarArquivo(File entrada, File saida) throws IOException {
        try {
            byte[] tudo = Files.readAllBytes(entrada.toPath());
            if (tudo.length < MAGIC.length + IV_BYTES || !temMagic(tudo)) {
                throw new IOException("Arquivo nao e um backup cifrado valido (magic ausente)");
            }
            int off = MAGIC.length;
            byte[] iv = Arrays.copyOfRange(tudo, off, off + IV_BYTES);
            byte[] ct = Arrays.copyOfRange(tudo, off + IV_BYTES, tudo.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plano = cipher.doFinal(ct);

            Files.write(saida.toPath(), plano);
        } catch (GeneralSecurityException e) {
            throw new IOException("Falha ao decifrar backup (chave errada ou arquivo corrompido?): " + e.getMessage(), e);
        }
    }

    private boolean temMagic(byte[] dados) {
        for (int i = 0; i < MAGIC.length; i++) {
            if (dados[i] != MAGIC[i]) return false;
        }
        return true;
    }

    private SecretKeySpec chave() throws IOException {
        SecretKeySpec local = chave;
        if (local == null) {
            synchronized (this) {
                local = chave;
                if (local == null) {
                    chave = local = carregarOuGerar();
                }
            }
        }
        return local;
    }

    private SecretKeySpec carregarOuGerar() throws IOException {
        File f = new File(keyFilePath);
        Properties props = new Properties();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                props.load(in);
            }
            String b64 = props.getProperty(PROP_CHAVE);
            if (b64 != null && !b64.isBlank()) {
                return new SecretKeySpec(Base64.getDecoder().decode(b64.trim()), "AES");
            }
        }

        byte[] raw = new byte[32];
        rng.nextBytes(raw);
        props.setProperty(PROP_CHAVE, Base64.getEncoder().encodeToString(raw));

        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            props.store(out, "Chave de cifra dos backups (AES-256-GCM). GUARDE ESTE ARQUIVO FORA DA MAQUINA - "
                    + "sem ele os backups .enc sao irrecuperaveis.");
        }
        log.warn("Chave de backup gerada em {} - GUARDE ESTE ARQUIVO FORA DA MAQUINA. "
                + "Sem ela os backups cifrados sao irrecuperaveis.", keyFilePath);
        return new SecretKeySpec(raw, "AES");
    }
}
