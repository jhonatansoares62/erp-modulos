package br.com.erpkit.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Credenciais do destino off-site (Cloudflare R2, S3-compatible), lidas de um arquivo
 * .properties FORA do banco — {@code C:\erpkit\config\<erp>\backup-offsite.properties}
 * (mesmo estilo file-based dos outros segredos). O off-site so fica ativo se a flag
 * {@code app.backup.offsite.enabled} for true E o arquivo tiver endpoint + credenciais.
 */
@Component
public class BackupOffsiteConfig {

    private static final Logger log = LoggerFactory.getLogger(BackupOffsiteConfig.class);

    private final boolean enabled;
    private final String configFile;
    private volatile Properties props;

    public BackupOffsiteConfig(
            @Value("${app.backup.offsite.enabled:false}") boolean enabled,
            @Value("${app.backup.offsite.config-file:C:/erpkit/config/erpodonto/backup-offsite.properties}") String configFile) {
        this.enabled = enabled;
        this.configFile = configFile;
    }

    /** Off-site utilizavel: flag ligada + arquivo com endpoint + access key presentes. */
    public boolean isEnabled() {
        return enabled && !endpoint().isBlank() && !accessKeyId().isBlank() && !secretAccessKey().isBlank();
    }

    /** Flag pura (independe do arquivo), pra mensagens de diagnostico. */
    public boolean isFlagLigada() {
        return enabled;
    }

    public String endpoint() {
        return get("r2.endpoint", "");
    }

    public String bucket() {
        return get("r2.bucket", "");
    }

    public String region() {
        return get("r2.region", "auto");
    }

    public String accessKeyId() {
        return get("r2.access-key-id", "");
    }

    public String secretAccessKey() {
        return get("r2.secret-access-key", "");
    }

    private String get(String chave, String padrao) {
        String v = carregar().getProperty(chave);
        return (v == null || v.isBlank()) ? padrao : v.trim();
    }

    private Properties carregar() {
        Properties local = props;
        if (local == null) {
            synchronized (this) {
                local = props;
                if (local == null) {
                    local = new Properties();
                    File f = new File(configFile);
                    if (f.exists()) {
                        try (InputStream in = new FileInputStream(f)) {
                            local.load(in);
                        } catch (IOException e) {
                            log.warn("Falha ao ler {}: {}", configFile, e.getMessage());
                        }
                    } else {
                        log.info("Config off-site nao encontrada em {} (off-site inativo ate configurar)", configFile);
                    }
                    props = local;
                }
            }
        }
        return local;
    }
}
