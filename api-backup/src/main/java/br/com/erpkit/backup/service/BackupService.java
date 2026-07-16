package br.com.erpkit.backup.service;

import br.com.erpkit.backup.config.BackupProperties;
import br.com.erpkit.backup.dto.BackupInfo;
import br.com.erpkit.backup.model.BackupHistorico;
import br.com.erpkit.backup.repository.BackupHistoricoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Orquestra o backup no modulo standalone. Diferenca-chave em relacao ao backup in-JVM do ERP:
 * o ALVO a dumpar (host/porta/usuario/senha do PostgreSQL do ERP + lista de bancos + banco
 * principal) vem 100% de {@link BackupProperties} (app.backup.target.* / databases / principal),
 * separado do datasource do proprio modulo (que so guarda o backup_historico). Sem ShedLock
 * (instancia unica); alerta de falha via {@link AlertaService}.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupProperties props;
    private final BackupHistoricoRepository historicoRepo;
    private final BackupCripto cripto;
    private final BackupOffsiteService offsiteService;
    private final AlertaService alertaService;

    public BackupService(BackupProperties props, BackupHistoricoRepository historicoRepo, BackupCripto cripto,
                         BackupOffsiteService offsiteService, AlertaService alertaService) {
        this.props = props;
        this.historicoRepo = historicoRepo;
        this.cripto = cripto;
        this.offsiteService = offsiteService;
        this.alertaService = alertaService;
    }

    private String host() {
        return props.getTarget().getHost();
    }

    private String port() {
        return String.valueOf(props.getTarget().getPort());
    }

    private String dbUser() {
        return props.getTarget().getUser();
    }

    private String dbPassword() {
        return props.getTarget().getPassword();
    }

    @Scheduled(cron = "${app.backup.cron:0 0 2 * * *}")
    public void backupAgendado() {
        if (!props.isEnabled()) return;
        executarBackupDiario();
    }

    /**
     * Catch-up no boot: se o backup automatico esta ligado e o ultimo backup bem-sucedido e
     * antigo (>26h) ou nunca houve, roda um agora — num thread daemon, sem atrasar o boot.
     * Cobre o caso do servico ter ficado desligado no horario agendado (02:00).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpNoBoot() {
        if (!props.isEnabled()) return;
        LocalDateTime ultimo = historicoRepo.findFirstByStatusOrderByCreatedAtDesc("ok")
                .map(BackupHistorico::getCreatedAt).orElse(null);
        if (!precisaCatchUp(ultimo, LocalDateTime.now())) return;
        log.info("Backup catch-up no boot: ultimo backup OK = {} — rodando agora", ultimo);
        Thread t = new Thread(this::executarBackupDiario, "backup-catchup");
        t.setDaemon(true);
        t.start();
    }

    /** Precisa de catch-up se nunca houve backup OK ou o ultimo passou de 26h. */
    static boolean precisaCatchUp(LocalDateTime ultimoOk, LocalDateTime agora) {
        return ultimoOk == null || ultimoOk.isBefore(agora.minusHours(26));
    }

    @Scheduled(cron = "${app.backup.restore-test-cron:0 30 3 * * SUN}")
    public void testarRestoreAgendado() {
        if (!props.isEnabled()) return;
        testarRestore();
    }

    /**
     * Teste de restore automatico: restaura o ultimo backup OK do banco principal num banco
     * descartavel PRE-CRIADO (app.backup.restore-test-db) e confere que da pra consultar.
     * Registra o resultado (banco='restore-test'). Nao cria/dropa o banco (evita exigir
     * CREATEDB em runtime). Como o backup e cifrado, isso tambem valida a decifragem.
     */
    public boolean testarRestore() {
        String principal = props.getPrincipal();
        BackupHistorico ultimo = historicoRepo
                .findFirstByBancoAndStatusOrderByCreatedAtDesc(principal, "ok").orElse(null);
        if (ultimo == null || ultimo.getArquivo() == null) {
            log.warn("Teste de restore: nenhum backup OK de {} pra testar", principal);
            registrarRestoreTest("erro", "sem backup para testar");
            return false;
        }
        File backupFile = new File(props.getDir(), ultimo.getArquivo());
        if (!backupFile.exists()) {
            log.warn("Teste de restore: arquivo {} nao existe mais", ultimo.getArquivo());
            registrarRestoreTest("erro", "arquivo do backup nao existe");
            return false;
        }

        File temp = null;
        try {
            File paraRestaurar = backupFile;
            if (backupFile.getName().endsWith(".enc")) {
                temp = File.createTempFile("verify_", ".dump");
                cripto.decifrarArquivo(backupFile, temp);
                paraRestaurar = temp;
            }
            if (!restaurarBackup(paraRestaurar, props.getRestoreTestDb())) {
                registrarRestoreTest("erro", "pg_restore falhou em " + props.getRestoreTestDb());
                return false;
            }
            boolean ok = sanidadeRestore();
            registrarRestoreTest(ok ? "ok" : "erro", ok ? null : "sanidade falhou (tabela usuario vazia/ausente)");
            log.info("Teste de restore: {} (restaurou {} em {})", ok ? "OK" : "FALHOU", ultimo.getArquivo(), props.getRestoreTestDb());
            return ok;
        } catch (IOException e) {
            log.error("Teste de restore: erro: {}", e.getMessage());
            registrarRestoreTest("erro", e.getMessage());
            return false;
        } finally {
            if (temp != null && temp.exists()) temp.delete();
        }
    }

    private boolean sanidadeRestore() {
        String url = "jdbc:postgresql://" + host() + ":" + port() + "/" + props.getRestoreTestDb();
        try (Connection c = DriverManager.getConnection(url, dbUser(), dbPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM usuario")) {
            return rs.next() && rs.getLong(1) >= 1;
        } catch (SQLException e) {
            log.warn("Teste de restore: sanidade falhou: {}", e.getMessage());
            return false;
        }
    }

    private void registrarRestoreTest(String status, String erroMsg) {
        try {
            BackupHistorico h = new BackupHistorico();
            h.setTipo("restore-test");
            h.setBanco("restore-test");
            h.setStatus(status);
            h.setVerificado("ok".equals(status));
            h.setOffsiteStatus("nao");
            if (erroMsg != null && erroMsg.length() > 500) erroMsg = erroMsg.substring(0, 500);
            h.setErroMsg(erroMsg);
            historicoRepo.save(h);
        } catch (Exception e) {
            log.warn("Nao registrou o teste de restore: {}", e.getMessage());
        }
    }

    /**
     * Backup diario resiliente: o banco principal e critico (formato custom -Fc + verificacao
     * via pg_restore --list); os bancos de modulo sao best-effort — a falha de um nao aborta o
     * backup do principal.
     */
    public List<File> executarBackupDiario() {
        String principal = props.getPrincipal();
        List<File> backups = new ArrayList<>();

        File principalBackup = executarBackupCustomFormat(principal, host(), port(), "diario");
        if (principalBackup == null) {
            log.error("Backup diario: FALHA no banco principal {} - dia comprometido", principal);
            alertaService.alertar("Falha no backup do banco principal (" + principal + ")");
        } else {
            backups.add(principalBackup);
        }

        for (String dbName : props.getDatabases().split(",")) {
            dbName = dbName.trim();
            if (dbName.isEmpty() || dbName.equals(principal)) continue;
            File backup = executarBackupCustomFormat(dbName, host(), port(), "diario");
            if (backup == null) {
                log.warn("Backup diario: falha no banco de modulo {} (continuando)", dbName);
            } else {
                backups.add(backup);
            }
        }

        File arquivosBackup = executarBackupArquivos("diario");
        if (arquivosBackup != null) {
            backups.add(arquivosBackup);
        }

        limparBackupsAntigos(new File(props.getDir()));
        return backups;
    }

    public File executarBackupCustomFormat(String dbName, String host, String port, String tipo) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("%s_%s.dump", dbName, timestamp);

        File dir = new File(props.getDir());
        if (!dir.exists()) dir.mkdirs();

        File backupFile = new File(dir, fileName);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    props.getPgDumpPath(),
                    "-h", host,
                    "-p", port,
                    "-U", dbUser(),
                    "-d", dbName,
                    "-Fc",
                    "-f", backupFile.getAbsolutePath(),
                    "--no-owner",
                    "--no-privileges"
            );
            pb.environment().put("PGPASSWORD", dbPassword());
            pb.redirectErrorStream(true);

            log.info("pg_dump cmd: [{}] -h {} -p {} -U {} -d {} -Fc -f {}", props.getPgDumpPath(), host, port, dbUser(), dbName, backupFile.getAbsolutePath());

            Process process = pb.start();
            String processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();
            log.info("pg_dump exit={}, output={}", exitCode, processOutput);

            if (exitCode == 0) {
                if (verificarBackup(backupFile)) {
                    log.info("Backup custom format realizado: {} ({} KB)", backupFile.getName(), backupFile.length() / 1024);
                    File finalFile = cifrarSeHabilitado(backupFile);
                    BackupHistorico hist = registrar(tipo, dbName, finalFile, "ok", true, null);
                    if (hist != null && offsiteService.isEnabled()) {
                        offsiteService.enviarAsync(finalFile, hist.getId());
                    }
                    return finalFile;
                } else {
                    log.error("Backup custom format falhou na verificacao: {}", backupFile.getName());
                    registrar(tipo, dbName, backupFile, "erro", false, "Falha na verificacao (pg_restore --list)");
                    return null;
                }
            } else {
                log.error("Falha no backup custom format (exit code {}): {}", exitCode, processOutput);
                registrar(tipo, dbName, null, "erro", false, "pg_dump exit " + exitCode + ": " + processOutput);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao executar backup custom format: {}", e.getMessage());
            registrar(tipo, dbName, null, "erro", false, e.getMessage());
        }

        return null;
    }

    private BackupHistorico registrar(String tipo, String banco, File arquivo, String status, boolean verificado, String erroMsg) {
        try {
            BackupHistorico h = new BackupHistorico();
            h.setTipo(tipo);
            h.setBanco(banco);
            if (arquivo != null) {
                h.setArquivo(arquivo.getName());
                if (arquivo.exists()) h.setTamanhoBytes(arquivo.length());
            }
            h.setStatus(status);
            h.setVerificado(verificado);
            if (erroMsg != null && erroMsg.length() > 500) {
                erroMsg = erroMsg.substring(0, 500);
            }
            h.setErroMsg(erroMsg);
            h.setOffsiteStatus(offsiteService.isEnabled() && "ok".equals(status) ? "pendente" : "nao");
            return historicoRepo.save(h);
        } catch (Exception e) {
            log.warn("Nao foi possivel registrar historico de backup ({}/{}): {}", tipo, banco, e.getMessage());
            return null;
        }
    }

    /**
     * Cifra o dump (ja verificado) se app.backup.encrypt=true e apaga o .dump em claro.
     * Em falha de cifra, mantem o .dump em claro (backup existente > backup nenhum) e loga.
     */
    private File cifrarSeHabilitado(File plano) {
        if (!props.isEncrypt()) return plano;
        File cifrado = new File(plano.getAbsolutePath() + ".enc");
        try {
            cripto.cifrarArquivo(plano, cifrado);
            if (!plano.delete()) {
                log.warn("Nao removeu o dump em claro apos cifrar: {}", plano.getName());
            }
            log.info("Backup cifrado: {}", cifrado.getName());
            return cifrado;
        } catch (IOException e) {
            log.error("Falha ao cifrar {} - mantendo o dump em claro: {}", plano.getName(), e.getMessage());
            cifrado.delete();
            return plano;
        }
    }

    /**
     * Backup file-level: zipa as pastas (app.backup.file-dirs) e arquivos avulsos
     * (app.backup.file-paths) — ex.: uploads do storage + whatsapp.key — verifica o zip,
     * cifra e (se off-site ativo) envia. Sem nada configurado/sem arquivos -> null.
     * NAO inclui a chave do proprio backup (backup.properties): seria circular.
     */
    public File executarBackupArquivos(String tipo) {
        List<File> fontes = new ArrayList<>();
        for (String d : props.getFileDirs().split(",")) {
            d = d.trim();
            if (!d.isEmpty()) fontes.add(new File(d));
        }
        for (String f : props.getFilePaths().split(",")) {
            f = f.trim();
            if (!f.isEmpty()) fontes.add(new File(f));
        }
        if (fontes.isEmpty()) return null;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File dir = new File(props.getDir());
        if (!dir.exists()) dir.mkdirs();
        File zip = new File(dir, "arquivos_" + timestamp + ".zip");

        try {
            int adicionados = zipar(fontes, zip);
            if (adicionados == 0) {
                log.info("Backup arquivos: nada a copiar (fontes vazias/inexistentes)");
                zip.delete();
                return null;
            }
            if (!verificarZip(zip)) {
                log.error("Backup arquivos: zip invalido {}", zip.getName());
                registrar(tipo, "arquivos", null, "erro", false, "zip invalido");
                zip.delete();
                return null;
            }
            log.info("Backup arquivos: {} arquivo(s) em {} ({} KB)", adicionados, zip.getName(), zip.length() / 1024);
            File finalFile = cifrarSeHabilitado(zip);
            BackupHistorico hist = registrar(tipo, "arquivos", finalFile, "ok", true, null);
            if (hist != null && offsiteService.isEnabled()) {
                offsiteService.enviarAsync(finalFile, hist.getId());
            }
            return finalFile;
        } catch (IOException e) {
            log.error("Backup arquivos: erro ao zipar: {}", e.getMessage());
            registrar(tipo, "arquivos", null, "erro", false, e.getMessage());
            if (zip.exists()) zip.delete();
            return null;
        }
    }

    private int zipar(List<File> fontes, File zipDestino) throws IOException {
        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipDestino)))) {
            for (File fonte : fontes) {
                if (!fonte.exists()) {
                    log.warn("Backup arquivos: fonte inexistente, pulando: {}", fonte.getAbsolutePath());
                    continue;
                }
                if (fonte.isDirectory()) {
                    Path base = fonte.toPath();
                    List<Path> arquivos;
                    try (Stream<Path> walk = Files.walk(base)) {
                        arquivos = walk.filter(Files::isRegularFile).collect(Collectors.toList());
                    }
                    for (Path p : arquivos) {
                        String entrada = fonte.getName() + "/" + base.relativize(p).toString().replace('\\', '/');
                        zos.putNextEntry(new ZipEntry(entrada));
                        Files.copy(p, zos);
                        zos.closeEntry();
                        count++;
                    }
                } else {
                    zos.putNextEntry(new ZipEntry(fonte.getName()));
                    Files.copy(fonte.toPath(), zos);
                    zos.closeEntry();
                    count++;
                }
            }
        }
        return count;
    }

    private boolean verificarZip(File zip) {
        try (ZipFile zf = new ZipFile(zip)) {
            return zf.size() > 0;
        } catch (IOException e) {
            log.error("Backup arquivos: falha ao verificar zip: {}", e.getMessage());
            return false;
        }
    }

    private boolean verificarBackup(File backupFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    props.getPgRestorePath(),
                    "--list",
                    backupFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            // Consumir output para evitar deadlock no buffer
            new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().forEach(line -> {});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao verificar backup: {}", e.getMessage());
            return false;
        }
    }

    public List<File> executarBackupTodosBancos() {
        List<File> backups = new ArrayList<>();

        for (String dbName : props.getDatabases().split(",")) {
            dbName = dbName.trim();
            if (dbName.isEmpty()) continue;
            File backup = executarBackupCustomFormat(dbName, host(), port(), "pre-update");
            if (backup == null) {
                log.error("Falha no backup de {}, abortando", dbName);
                return null;
            }
            backups.add(backup);
        }

        limparBackupsAntigos(new File(props.getDir()));
        return backups;
    }

    public boolean restaurarBackup(File backupFile, String dbName) {
        File paraRestaurar = backupFile;
        File tempDecifrado = null;
        if (backupFile.getName().endsWith(".enc")) {
            try {
                tempDecifrado = File.createTempFile("restore_", ".dump");
                cripto.decifrarArquivo(backupFile, tempDecifrado);
                paraRestaurar = tempDecifrado;
            } catch (IOException e) {
                log.error("Falha ao decifrar backup para restore: {}", e.getMessage());
                if (tempDecifrado != null) tempDecifrado.delete();
                return false;
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    props.getPgRestorePath(),
                    "-h", host(),
                    "-p", port(),
                    "-U", dbUser(),
                    "-d", dbName,
                    "--clean",
                    "--if-exists",
                    "--no-owner",
                    "--no-privileges",
                    paraRestaurar.getAbsolutePath()
            );
            pb.environment().put("PGPASSWORD", dbPassword());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Banco {} restaurado com sucesso de {}", dbName, backupFile.getName());
                return true;
            } else {
                String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                        .lines().collect(Collectors.joining("\n"));
                log.error("Falha ao restaurar banco {} (exit code {}): {}", dbName, exitCode, output);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao restaurar backup: {}", e.getMessage());
        } finally {
            if (tempDecifrado != null && tempDecifrado.exists() && !tempDecifrado.delete()) {
                log.warn("Nao removeu o arquivo temporario de restore: {}", tempDecifrado.getName());
            }
        }

        return false;
    }

    public List<BackupInfo> listarBackups() {
        File dir = new File(props.getDir());
        if (!dir.exists()) {
            return List.of();
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".sql") || name.endsWith(".dump") || name.endsWith(".enc"));
        if (files == null) {
            return List.of();
        }

        return Arrays.stream(files)
                .map(BackupInfo::new)
                .sorted(Comparator.comparing(BackupInfo::getData).reversed())
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", props.isEnabled());
        status.put("encrypt", props.isEncrypt());
        status.put("offsiteEnabled", offsiteService.isEnabled());
        status.put("cron", props.getCron());
        status.put("retention", Map.of(
                "daily", props.getRetention().getDaily(),
                "weekly", props.getRetention().getWeekly(),
                "monthly", props.getRetention().getMonthly()));
        status.put("databases", Arrays.stream(props.getDatabases().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));

        BackupHistorico ultimo = historicoRepo.findFirstByOrderByCreatedAtDesc().orElse(null);
        if (ultimo != null) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("data", ultimo.getCreatedAt());
            u.put("banco", ultimo.getBanco());
            u.put("tipo", ultimo.getTipo());
            u.put("status", ultimo.getStatus());
            u.put("verificado", ultimo.isVerificado());
            u.put("offsiteStatus", ultimo.getOffsiteStatus());
            u.put("tamanhoBytes", ultimo.getTamanhoBytes());
            status.put("ultimoBackup", u);
        } else {
            status.put("ultimoBackup", null);
        }
        return status;
    }

    public List<BackupHistorico> listarHistorico() {
        return historicoRepo.findTop100ByOrderByCreatedAtDesc();
    }

    public String getBackupDir() {
        return props.getDir();
    }

    /** Retencao GFS: mantem os ultimos N dias, 1 por semana (W) e 1 por mes (M); apaga o resto. */
    private void limparBackupsAntigos(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".sql") || name.endsWith(".dump") || name.endsWith(".enc"));
        if (files == null || files.length == 0) return;

        Map<LocalDate, List<File>> porDia = new java.util.HashMap<>();
        for (File f : files) {
            LocalDate dia = Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate();
            porDia.computeIfAbsent(dia, k -> new ArrayList<>()).add(f);
        }
        List<LocalDate> diasDesc = porDia.keySet().stream()
                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        Set<LocalDate> manter = diasParaManter(diasDesc,
                props.getRetention().getDaily(), props.getRetention().getWeekly(), props.getRetention().getMonthly());

        for (LocalDate dia : diasDesc) {
            if (manter.contains(dia)) continue;
            for (File f : porDia.get(dia)) {
                if (f.delete()) {
                    log.info("Backup antigo removido (retencao GFS): {}", f.getName());
                }
            }
        }
    }

    /**
     * GFS: dias a manter = os {@code daily} mais recentes + o dia mais recente de cada uma das
     * {@code weekly} semanas mais recentes + o dia mais recente de cada um dos {@code monthly}
     * meses mais recentes. {@code diasDesc} vem ordenado do mais recente pro mais antigo.
     */
    static Set<LocalDate> diasParaManter(List<LocalDate> diasDesc, int daily, int weekly, int monthly) {
        Set<LocalDate> manter = new HashSet<>();
        for (int i = 0; i < Math.min(daily, diasDesc.size()); i++) {
            manter.add(diasDesc.get(i));
        }
        WeekFields wf = WeekFields.ISO;
        Set<String> semanas = new HashSet<>();
        for (LocalDate d : diasDesc) {
            String chave = d.get(wf.weekBasedYear()) + "-W" + d.get(wf.weekOfWeekBasedYear());
            if (semanas.add(chave) && semanas.size() <= weekly) {
                manter.add(d);
            }
        }
        Set<String> meses = new HashSet<>();
        for (LocalDate d : diasDesc) {
            String chave = d.getYear() + "-" + d.getMonthValue();
            if (meses.add(chave) && meses.size() <= monthly) {
                manter.add(d);
            }
        }
        return manter;
    }
}
