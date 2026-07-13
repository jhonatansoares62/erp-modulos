package br.com.erpkit.whatsapp.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cofre de campo — cifra/decifra strings em repouso com AES-256-GCM (LGPD, Art. 46-49).
 *
 * <p>Usado pelo {@link CampoCifradoConverter} (JPA {@code @Convert}) para proteger os
 * secrets em {@code whatsapp.config_meta} (e, adiante, o conteudo de conversa em
 * {@code mensagens_log}). Objetivo concreto: um {@code pg_dump}/backup que saia da clinica
 * NAO expoe token/segredo em claro — a chave mora FORA do banco. Nao protege contra o admin
 * da propria maquina (on-premise, ameaca inatingivel por natureza).
 *
 * <h2>Formato</h2>
 * Valor cifrado = {@code "v1:" + base64(iv || ciphertext||tag)}. O prefixo {@code v1:}
 * versiona o esquema E permite distinguir cifrado de <b>texto plano legado</b> — a leitura
 * de um valor sem prefixo o devolve como esta, o que torna a migracao <b>sem downtime</b>
 * (linhas antigas continuam legiveis; cada escrita passa a cifrar).
 *
 * <h2>Chave</h2>
 * Resolvida uma vez (lazy, thread-safe), nesta ordem:
 * <ol>
 *   <li>chave crua base64 em {@code -Dwhatsapp.enc.key} / env {@code WHATSAPP_ENC_KEY};</li>
 *   <li>arquivo em {@code -Dwhatsapp.enc.key.file} / env {@code WHATSAPP_ENC_KEY_FILE}
 *       (default {@code whatsapp-enc.key} no diretorio de trabalho) — <b>auto-gerado</b>
 *       (32 bytes seguros) se ausente.</li>
 * </ol>
 * No instalado, o servico injeta {@code WHATSAPP_ENC_KEY_FILE=C:\erpkit\config\<erp>\modulos\whatsapp.key}.
 * A chave herda a ACL do diretorio (mesmo padrao dos outros secrets do erpkit). <b>Sem a
 * chave o conteudo cifrado e irrecuperavel</b> — ela precisa entrar no backup de arquivos.
 *
 * <h2>Regras de falha</h2>
 * <ul>
 *   <li>{@link #decifrar(String)} <b>nunca lanca</b>: em chave trocada/valor corrompido loga
 *       e devolve {@code null} — protege queries/inbox e deixa o {@code MetaConfigService}
 *       cair no seed de env var (o boot sobrevive).</li>
 *   <li>{@link #cifrar(String)} <b>falha alto</b> (escrita): melhor abortar a transacao do
 *       que gravar em claro/corrompido.</li>
 * </ul>
 */
public final class CampoCripto {

    private static final Logger log = LoggerFactory.getLogger(CampoCripto.class);

    static final String PREFIXO = "v1:";
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String DEFAULT_KEY_FILE = "whatsapp-enc.key";

    private static final SecureRandom RNG = new SecureRandom();

    private static volatile SecretKey chave;

    private CampoCripto() {
    }

    /** Cifra texto plano. {@code null}/vazio passam sem alteracao. */
    public static String cifrar(String plano) {
        if (plano == null || plano.isEmpty()) {
            return plano;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plano.getBytes(StandardCharsets.UTF_8));

            byte[] saida = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, saida, 0, iv.length);
            System.arraycopy(ct, 0, saida, iv.length, ct.length);
            return PREFIXO + Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar campo em repouso", e);
        }
    }

    /**
     * Decifra um valor gravado. Devolve texto plano legado (sem prefixo) como esta.
     * Nunca lanca: falha de decifragem loga e retorna {@code null}.
     */
    public static String decifrar(String armazenado) {
        if (armazenado == null || armazenado.isEmpty()) {
            return armazenado;
        }
        if (!armazenado.startsWith(PREFIXO)) {
            return armazenado; // texto plano legado — leitura tolerante (migracao sem downtime)
        }
        try {
            byte[] entrada = Base64.getDecoder().decode(armazenado.substring(PREFIXO.length()));
            byte[] iv = Arrays.copyOfRange(entrada, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plano = cipher.doFinal(Arrays.copyOfRange(entrada, IV_BYTES, entrada.length));
            return new String(plano, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            log.warn("Falha ao decifrar campo (chave trocada/valor corrompido?) — retornando null: {}",
                    e.getMessage());
            return null;
        }
    }

    private static SecretKey chave() {
        SecretKey k = chave;
        if (k == null) {
            synchronized (CampoCripto.class) {
                k = chave;
                if (k == null) {
                    k = carregarOuGerarChave();
                    chave = k;
                }
            }
        }
        return k;
    }

    private static SecretKey carregarOuGerarChave() {
        String bruta = valor("whatsapp.enc.key", "WHATSAPP_ENC_KEY");
        if (bruta != null && !bruta.isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(bruta.trim());
            log.info("Chave de cripto carregada de variavel (WHATSAPP_ENC_KEY).");
            return new SecretKeySpec(bytes, "AES");
        }

        String caminho = valor("whatsapp.enc.key.file", "WHATSAPP_ENC_KEY_FILE");
        Path arquivo = Path.of(caminho == null || caminho.isBlank() ? DEFAULT_KEY_FILE : caminho.trim());
        try {
            if (Files.exists(arquivo)) {
                byte[] bytes = Base64.getDecoder().decode(Files.readString(arquivo, StandardCharsets.UTF_8).trim());
                log.info("Chave de cripto carregada de {}.", arquivo.toAbsolutePath());
                return new SecretKeySpec(bytes, "AES");
            }
            byte[] nova = new byte[32]; // AES-256
            RNG.nextBytes(nova);
            if (arquivo.getParent() != null) {
                Files.createDirectories(arquivo.getParent());
            }
            Files.writeString(arquivo, Base64.getEncoder().encodeToString(nova), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            log.warn("Chave de cripto GERADA em {} — inclua no backup de arquivos; sem ela o conteudo "
                    + "cifrado do banco e irrecuperavel.", arquivo.toAbsolutePath());
            return new SecretKeySpec(nova, "AES");
        } catch (java.nio.file.FileAlreadyExistsException corrida) {
            try {
                byte[] bytes = Base64.getDecoder().decode(Files.readString(arquivo, StandardCharsets.UTF_8).trim());
                return new SecretKeySpec(bytes, "AES");
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao reler a chave de cripto em " + arquivo, e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar/gerar a chave de cripto em " + arquivo, e);
        }
    }

    private static String valor(String sysProp, String env) {
        String v = System.getProperty(sysProp);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return v;
    }
}
