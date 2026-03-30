package br.com.erpkit.storage.service;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.storage.dto.ArquivoResponse;
import br.com.erpkit.storage.dto.StorageEstatisticasResponse;
import br.com.erpkit.storage.model.Arquivo;
import br.com.erpkit.storage.repository.ArquivoRepository;
import jakarta.annotation.PostConstruct;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final Set<String> TIPOS_IMAGEM = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");
    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final ArquivoRepository arquivoRepository;

    @Value("${modulo.storage.diretorio:./uploads}")
    private String diretorioBase;

    @Value("${modulo.storage.max-tamanho-mb:50}")
    private int maxTamanhoMb;

    @Value("${modulo.storage.thumbnail-largura:200}")
    private int thumbnailLargura;

    @Value("${modulo.storage.thumbnail-altura:200}")
    private int thumbnailAltura;

    @Value("${modulo.storage.base-url:http://localhost:8085}")
    private String baseUrl;

    public StorageService(ArquivoRepository arquivoRepository) {
        this.arquivoRepository = arquivoRepository;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(diretorioBase));
            log.info("Storage inicializado em: {}", Paths.get(diretorioBase).toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar diretório de storage: " + diretorioBase, e);
        }
    }

    public ArquivoResponse upload(MultipartFile file, String categoria, String origem, String referenciaId) {
        validarArquivo(file);

        String nomeOriginal = file.getOriginalFilename();
        String extensao = extrairExtensao(nomeOriginal);
        String nomeArmazenado = UUID.randomUUID() + extensao;
        String subDir = LocalDate.now().format(DIR_FORMAT);

        try {
            Path diretorio = Paths.get(diretorioBase, subDir);
            Files.createDirectories(diretorio);

            Path destino = diretorio.resolve(nomeArmazenado);
            Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            Arquivo arquivo = new Arquivo();
            arquivo.setNomeOriginal(nomeOriginal);
            arquivo.setNomeArmazenado(subDir + "/" + nomeArmazenado);
            arquivo.setContentType(file.getContentType());
            arquivo.setTamanho(file.getSize());
            arquivo.setCategoria(categoria);
            arquivo.setOrigem(origem);
            arquivo.setReferenciaId(referenciaId);

            if (TIPOS_IMAGEM.contains(file.getContentType())) {
                gerarThumbnail(destino, diretorio, nomeArmazenado);
                arquivo.setTemThumbnail(true);
            }

            arquivo = arquivoRepository.save(arquivo);
            log.info("Arquivo salvo: id={}, nome={}, tamanho={}KB", arquivo.getId(), nomeOriginal, file.getSize() / 1024);
            return toResponse(arquivo);

        } catch (IOException e) {
            throw new ModuloException("Erro ao salvar arquivo: " + e.getMessage());
        }
    }

    public List<ArquivoResponse> uploadMultiplo(MultipartFile[] files, String categoria, String origem, String referenciaId) {
        return java.util.Arrays.stream(files)
                .map(file -> upload(file, categoria, origem, referenciaId))
                .toList();
    }

    public ArquivoResponse buscar(Long id) {
        Arquivo arquivo = buscarAtivo(id);
        return toResponse(arquivo);
    }

    public Page<ArquivoResponse> listar(String categoria, String origem, Pageable pageable) {
        Page<Arquivo> page;
        if (categoria != null && !categoria.isBlank()) {
            page = arquivoRepository.findByCategoriaAndAtivoTrue(categoria, pageable);
        } else if (origem != null && !origem.isBlank()) {
            page = arquivoRepository.findByOrigemAndAtivoTrue(origem, pageable);
        } else {
            page = arquivoRepository.findByAtivoTrue(pageable);
        }
        return page.map(this::toResponse);
    }

    public List<ArquivoResponse> listarPorReferencia(String referenciaId) {
        return arquivoRepository.findByReferenciaIdAndAtivoTrue(referenciaId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Resource download(Long id) {
        Arquivo arquivo = buscarAtivo(id);
        return carregarRecurso(arquivo.getNomeArmazenado());
    }

    public Resource downloadThumbnail(Long id) {
        Arquivo arquivo = buscarAtivo(id);
        if (!arquivo.isTemThumbnail()) {
            throw new ModuloException("Arquivo não possui thumbnail", HttpStatus.NOT_FOUND);
        }
        String nomeThumb = gerarNomeThumb(arquivo.getNomeArmazenado());
        return carregarRecurso(nomeThumb);
    }

    public void softDelete(Long id) {
        Arquivo arquivo = buscarAtivo(id);
        arquivo.setAtivo(false);
        arquivoRepository.save(arquivo);
        log.info("Arquivo desativado: id={}, nome={}", id, arquivo.getNomeOriginal());
    }

    public StorageEstatisticasResponse estatisticas() {
        List<Object[]> stats = arquivoRepository.estatisticasPorCategoria();

        long totalArquivos = 0;
        long totalBytes = 0;
        Map<String, StorageEstatisticasResponse.CategoriaStats> porCategoria = new HashMap<>();

        for (Object[] row : stats) {
            String cat = row[0] != null ? (String) row[0] : "sem-categoria";
            long qtd = (Long) row[1];
            long bytes = (Long) row[2];
            totalArquivos += qtd;
            totalBytes += bytes;
            porCategoria.put(cat, new StorageEstatisticasResponse.CategoriaStats(qtd, bytes, formatarTamanho(bytes)));
        }

        StorageEstatisticasResponse response = new StorageEstatisticasResponse();
        response.setTotalArquivos(totalArquivos);
        response.setTotalBytes(totalBytes);
        response.setTotalFormatado(formatarTamanho(totalBytes));
        response.setPorCategoria(porCategoria);
        return response;
    }

    private Arquivo buscarAtivo(Long id) {
        Arquivo arquivo = arquivoRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Arquivo não encontrado", HttpStatus.NOT_FOUND));
        if (!arquivo.isAtivo()) {
            throw new ModuloException("Arquivo não encontrado", HttpStatus.NOT_FOUND);
        }
        return arquivo;
    }

    private Resource carregarRecurso(String nomeArmazenado) {
        try {
            Path path = Paths.get(diretorioBase).resolve(nomeArmazenado);
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) {
                throw new ModuloException("Arquivo físico não encontrado", HttpStatus.NOT_FOUND);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ModuloException("Erro ao carregar arquivo: " + e.getMessage());
        }
    }

    private void gerarThumbnail(Path original, Path diretorio, String nomeArmazenado) {
        try {
            String nomeThumb = "thumb_" + nomeArmazenado;
            Path destThumb = diretorio.resolve(nomeThumb);
            Thumbnails.of(original.toFile())
                    .size(thumbnailLargura, thumbnailAltura)
                    .keepAspectRatio(true)
                    .toFile(destThumb.toFile());
        } catch (IOException e) {
            log.warn("Não foi possível gerar thumbnail para {}: {}", nomeArmazenado, e.getMessage());
        }
    }

    private String gerarNomeThumb(String nomeArmazenado) {
        int lastSlash = nomeArmazenado.lastIndexOf('/');
        if (lastSlash >= 0) {
            return nomeArmazenado.substring(0, lastSlash + 1) + "thumb_" + nomeArmazenado.substring(lastSlash + 1);
        }
        return "thumb_" + nomeArmazenado;
    }

    private void validarArquivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ModuloException("Arquivo é obrigatório");
        }
        long maxBytes = (long) maxTamanhoMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ModuloException("Arquivo excede o tamanho máximo de " + maxTamanhoMb + "MB");
        }
    }

    private String extrairExtensao(String nomeOriginal) {
        if (nomeOriginal == null || !nomeOriginal.contains(".")) {
            return "";
        }
        return nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
    }

    private String formatarTamanho(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private ArquivoResponse toResponse(Arquivo arquivo) {
        ArquivoResponse response = new ArquivoResponse();
        response.setId(arquivo.getId());
        response.setNomeOriginal(arquivo.getNomeOriginal());
        response.setContentType(arquivo.getContentType());
        response.setTamanho(arquivo.getTamanho());
        response.setCategoria(arquivo.getCategoria());
        response.setOrigem(arquivo.getOrigem());
        response.setReferenciaId(arquivo.getReferenciaId());
        response.setTemThumbnail(arquivo.isTemThumbnail());
        response.setUrlDownload(baseUrl + "/api/arquivos/" + arquivo.getId() + "/download");
        if (arquivo.isTemThumbnail()) {
            response.setUrlThumbnail(baseUrl + "/api/arquivos/" + arquivo.getId() + "/thumbnail");
        }
        response.setCriadoEm(arquivo.getCriadoEm());
        return response;
    }
}
