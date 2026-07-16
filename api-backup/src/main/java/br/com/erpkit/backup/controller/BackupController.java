package br.com.erpkit.backup.controller;

import br.com.erpkit.backup.dto.BackupInfo;
import br.com.erpkit.backup.model.BackupHistorico;
import br.com.erpkit.backup.service.BackupOffsiteService;
import br.com.erpkit.backup.service.BackupService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * API do modulo de backup. Guardada por X-API-Key (BackupApiKeyFilter, /v1/**). O ERP consome
 * via proxy (/api/modulos/backup/* -> aqui). Mesma superficie da tela atual do ERP
 * (status/historico/executar/restaurar/testar-restore/offsite/listar), reapontada no cutover.
 */
@RestController
@RequestMapping("/v1/backup")
public class BackupController {

    private final BackupService backupService;
    private final BackupOffsiteService offsiteService;

    public BackupController(BackupService backupService, BackupOffsiteService offsiteService) {
        this.backupService = backupService;
        this.offsiteService = offsiteService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(backupService.getStatus());
    }

    @GetMapping("/historico")
    public ResponseEntity<List<BackupHistorico>> historico() {
        return ResponseEntity.ok(backupService.listarHistorico());
    }

    @GetMapping("/listar")
    public ResponseEntity<List<BackupInfo>> listar() {
        return ResponseEntity.ok(backupService.listarBackups());
    }

    @PostMapping("/executar")
    public ResponseEntity<Map<String, Object>> executarAgora() {
        List<File> files = backupService.executarBackupDiario();
        return ResponseEntity.ok(Map.of(
                "status", (files == null || files.isEmpty()) ? "erro" : "ok",
                "quantidade", files == null ? 0 : files.size(),
                "arquivos", files == null ? List.of() : files.stream().map(File::getName).toList()
        ));
    }

    @PostMapping("/pre-update")
    public ResponseEntity<Map<String, Object>> backupPreUpdate() {
        List<File> backups = backupService.executarBackupTodosBancos();
        if (backups != null && !backups.isEmpty()) {
            List<Map<String, String>> arquivos = backups.stream()
                    .map(f -> Map.of("nome", f.getName(), "tamanho", (f.length() / 1024) + " KB"))
                    .toList();
            return ResponseEntity.ok(Map.of("status", "ok", "arquivos", arquivos));
        }
        return ResponseEntity.internalServerError()
                .body(Map.of("status", "erro", "message", "Falha no backup pre-update"));
    }

    @PostMapping("/testar-restore")
    public ResponseEntity<Map<String, Object>> testarRestore() {
        boolean ok = backupService.testarRestore();
        return ResponseEntity.ok(Map.of("status", ok ? "ok" : "erro"));
    }

    @PostMapping("/offsite/testar")
    public ResponseEntity<Map<String, Object>> testarOffsite() {
        boolean ok = offsiteService.testarConexao();
        return ResponseEntity.ok(Map.of(
                "status", ok ? "ok" : "erro",
                "enabled", offsiteService.isEnabled()
        ));
    }

    @PostMapping("/restaurar")
    public ResponseEntity<Map<String, String>> restaurar(@RequestParam String arquivo, @RequestParam String banco) {
        File backupFile = new File(backupService.getBackupDir(), arquivo);
        if (!backupFile.exists()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "erro", "message", "Arquivo nao encontrado"));
        }
        boolean sucesso = backupService.restaurarBackup(backupFile, banco);
        if (sucesso) {
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Banco " + banco + " restaurado com sucesso"));
        }
        return ResponseEntity.internalServerError()
                .body(Map.of("status", "erro", "message", "Falha ao restaurar banco"));
    }

    /** Baixa um backup EXISTENTE pelo nome (a tela lista antes). Nao regenera nada. */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String arquivo) {
        File file = new File(backupService.getBackupDir(), arquivo);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
