package br.com.erpkit.backup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config do modulo de backup (prefixo {@code app.backup}). O ponto-chave da extracao: o
 * ALVO a dumpar ({@link Target} + {@link #databases} + {@link #principal}) vem por config,
 * separado do datasource do proprio modulo (que guarda so o backup_historico). Assim o mesmo
 * jar serve qualquer ERP — cada instalacao aponta seu PostgreSQL e sua lista de bancos.
 */
@Component
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    /** Liga o agendamento (diario + restore-test) e o catch-up no boot. */
    private boolean enabled = false;
    /** Diretorio local dos backups. */
    private String dir = "backups";
    private String cron = "0 0 2 * * *";
    private String restoreTestCron = "0 30 3 * * SUN";
    /** Cifra AES-256-GCM em repouso (chave em arquivo, ver BackupCripto). */
    private boolean encrypt = true;
    private String pgDumpPath = "pg_dump";
    private String pgRestorePath = "pg_restore";
    /** Lista de bancos a dumpar (CSV). O primeiro/principal tambem em {@link #principal}. */
    private String databases = "";
    /** Banco principal (fonte do restore-test e do status). */
    private String principal = "";
    /** Banco descartavel pre-criado onde o restore-test restaura (sem CREATEDB em runtime). */
    private String restoreTestDb = "db_verify_restore";
    /** Pastas a incluir no backup file-level (CSV) — ex.: uploads do storage. */
    private String fileDirs = "";
    /** Arquivos avulsos no backup file-level (CSV) — ex.: whatsapp.key. */
    private String filePaths = "";
    /** Chave de licenca = prefixo do cliente no bucket off-site. Vazio ⇒ off-site inativo. */
    private String licenseKey = "";

    private final Retention retention = new Retention();
    private final Target target = new Target();
    private final Alert alert = new Alert();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public String getRestoreTestCron() { return restoreTestCron; }
    public void setRestoreTestCron(String restoreTestCron) { this.restoreTestCron = restoreTestCron; }
    public boolean isEncrypt() { return encrypt; }
    public void setEncrypt(boolean encrypt) { this.encrypt = encrypt; }
    public String getPgDumpPath() { return pgDumpPath; }
    public void setPgDumpPath(String pgDumpPath) { this.pgDumpPath = pgDumpPath; }
    public String getPgRestorePath() { return pgRestorePath; }
    public void setPgRestorePath(String pgRestorePath) { this.pgRestorePath = pgRestorePath; }
    public String getDatabases() { return databases; }
    public void setDatabases(String databases) { this.databases = databases; }
    public String getPrincipal() { return principal; }
    public void setPrincipal(String principal) { this.principal = principal; }
    public String getRestoreTestDb() { return restoreTestDb; }
    public void setRestoreTestDb(String restoreTestDb) { this.restoreTestDb = restoreTestDb; }
    public String getFileDirs() { return fileDirs; }
    public void setFileDirs(String fileDirs) { this.fileDirs = fileDirs; }
    public String getFilePaths() { return filePaths; }
    public void setFilePaths(String filePaths) { this.filePaths = filePaths; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }
    public Retention getRetention() { return retention; }
    public Target getTarget() { return target; }
    public Alert getAlert() { return alert; }

    /** Retencao GFS: dias, semanas e meses a manter. */
    public static class Retention {
        private int daily = 14;
        private int weekly = 8;
        private int monthly = 12;

        public int getDaily() { return daily; }
        public void setDaily(int daily) { this.daily = daily; }
        public int getWeekly() { return weekly; }
        public void setWeekly(int weekly) { this.weekly = weekly; }
        public int getMonthly() { return monthly; }
        public void setMonthly(int monthly) { this.monthly = monthly; }
    }

    /** PostgreSQL do ERP a dumpar (separado do datasource do modulo). */
    public static class Target {
        private String host = "localhost";
        private int port = 5432;
        private String user = "";
        private String password = "";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** Destino do alerta de falha (e-mail da clinica). Vazio ⇒ sem alerta. */
    public static class Alert {
        private String email = "";
        private String nome = "";

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
    }
}
