package br.com.erpkit.storage.controller;

import br.com.erpkit.storage.dto.ArquivoResponse;
import br.com.erpkit.storage.dto.StorageEstatisticasResponse;
import br.com.erpkit.storage.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/arquivos")
public class ArquivoController {

    private final StorageService storageService;

    public ArquivoController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArquivoResponse> upload(
            @RequestParam("arquivo") MultipartFile file,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String referenciaId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storageService.upload(file, categoria, origem, referenciaId));
    }

    @PostMapping(value = "/multiplo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ArquivoResponse>> uploadMultiplo(
            @RequestParam("arquivos") MultipartFile[] files,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String referenciaId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storageService.uploadMultiplo(files, categoria, origem, referenciaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArquivoResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(storageService.buscar(id));
    }

    @GetMapping
    public ResponseEntity<Page<ArquivoResponse>> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String origem,
            Pageable pageable) {
        return ResponseEntity.ok(storageService.listar(categoria, origem, pageable));
    }

    @GetMapping("/referencia/{referenciaId}")
    public ResponseEntity<List<ArquivoResponse>> listarPorReferencia(@PathVariable String referenciaId) {
        return ResponseEntity.ok(storageService.listarPorReferencia(referenciaId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ArquivoResponse info = storageService.buscar(id);
        Resource resource = storageService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + info.getNomeOriginal() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long id) {
        Resource resource = storageService.downloadThumbnail(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        storageService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/estatisticas")
    public ResponseEntity<StorageEstatisticasResponse> estatisticas() {
        return ResponseEntity.ok(storageService.estatisticas());
    }
}
