package br.com.erpkit.backup.service;

import br.com.erpkit.backup.config.BackupProperties;
import br.com.erpkit.backup.repository.BackupHistoricoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orquestra o envio dos backups para o destino off-site (R2), isolando por cliente via
 * prefixo = chave de licenca. No modulo standalone a chave vem de {@code app.backup.license-key}
 * (config), no lugar do {@code LicenseService} do ERP.
 */
@Service
public class BackupOffsiteService {

    private static final Logger log = LoggerFactory.getLogger(BackupOffsiteService.class);
    private static final int TENTATIVAS = 3;

    private final R2Client r2;
    private final BackupOffsiteConfig cfg;
    private final BackupProperties props;
    private final BackupHistoricoRepository historicoRepo;
    private final ExecutorService uploadExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "backup-offsite-upload");
        t.setDaemon(true);
        return t;
    });

    public BackupOffsiteService(R2Client r2, BackupOffsiteConfig cfg, BackupProperties props,
                                BackupHistoricoRepository historicoRepo) {
        this.r2 = r2;
        this.cfg = cfg;
        this.props = props;
        this.historicoRepo = historicoRepo;
    }

    public boolean isEnabled() {
        // Exige licenca: o prefixo do bucket E a chave de licenca (isolamento por cliente).
        // Sem ela (instalacao ainda nao ativada) nao ha id unico -> nao sobe, pra nao
        // colidir com outra instalacao num prefixo generico.
        return cfg.isEnabled() && !licenseKey().isBlank();
    }

    private String licenseKey() {
        String lk = props.getLicenseKey();
        return lk == null ? "" : lk.trim();
    }

    /** Prefixo por cliente no bucket (isola os dados entre instalacoes). */
    public String prefixo() {
        String lk = licenseKey();
        return lk.isBlank() ? "sem-licenca" : lk;
    }

    public boolean testarConexao() {
        if (!cfg.isEnabled()) {
            log.warn("Off-site inativo (flag {}, credenciais {}) — teste ignorado",
                    cfg.isFlagLigada() ? "ligada" : "desligada",
                    cfg.accessKeyId().isBlank() ? "ausentes" : "presentes");
            return false;
        }
        return r2.testarConexao(prefixo());
    }

    /** Enfileira o envio do arquivo pro R2 (assincrono; nao bloqueia nem derruba o backup local). */
    public void enviarAsync(File arquivo, Long histId) {
        if (!cfg.isEnabled()) return;
        uploadExec.submit(() -> enviarComRetry(arquivo, histId));
    }

    private void enviarComRetry(File arquivo, Long histId) {
        String key = prefixo() + "/" + arquivo.getName();
        for (int i = 1; i <= TENTATIVAS; i++) {
            try {
                int st = r2.putObject(key, arquivo, "application/octet-stream");
                if (st >= 200 && st < 300) {
                    atualizarStatus(histId, "enviado");
                    log.info("Backup off-site enviado: {} ({} KB)", key, arquivo.length() / 1024);
                    return;
                }
                log.warn("Off-site {} tentativa {}/{}: HTTP {}", key, i, TENTATIVAS, st);
            } catch (Exception e) {
                log.warn("Off-site {} tentativa {}/{} falhou: {}", key, i, TENTATIVAS, e.getMessage());
            }
            dormir(i);
        }
        atualizarStatus(histId, "falha");
        log.error("Backup off-site FALHOU apos {} tentativas: {}", TENTATIVAS, key);
    }

    private void atualizarStatus(Long histId, String status) {
        if (histId == null) return;
        try {
            historicoRepo.findById(histId).ifPresent(h -> {
                h.setOffsiteStatus(status);
                historicoRepo.save(h);
            });
        } catch (Exception e) {
            log.warn("Falha ao atualizar offsite_status do backup {}: {}", histId, e.getMessage());
        }
    }

    private void dormir(int tentativa) {
        try {
            Thread.sleep(1000L * tentativa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
