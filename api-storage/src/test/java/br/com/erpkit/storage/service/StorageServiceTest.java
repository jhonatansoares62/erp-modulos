package br.com.erpkit.storage.service;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.storage.dto.ArquivoResponse;
import br.com.erpkit.storage.dto.StorageEstatisticasResponse;
import br.com.erpkit.storage.repository.ArquivoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class StorageServiceTest {

    @Autowired
    private StorageService storageService;

    @Autowired
    private ArquivoRepository arquivoRepository;

    @Value("${modulo.storage.diretorio}")
    private String diretorioBase;

    @BeforeEach
    void setUp() {
        arquivoRepository.deleteAll();
    }

    @AfterEach
    void tearDown() throws IOException {
        arquivoRepository.deleteAll();
        // Limpar diretorio temporario de testes
        Path dir = Paths.get(diretorioBase);
        if (Files.exists(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Test
    @DisplayName("Deve fazer upload de arquivo e retornar resposta com dados corretos")
    void deveUploadArquivoERetornarResposta() {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "documento.pdf", "application/pdf", "conteudo pdf".getBytes());

        ArquivoResponse response = storageService.upload(file, "documentos", "erp-calhas", "OS-123");

        assertNotNull(response.getId(), "ID nao deve ser nulo");
        assertEquals("documento.pdf", response.getNomeOriginal(), "Nome original deve corresponder");
        assertEquals("application/pdf", response.getContentType(), "Content type deve corresponder");
        assertEquals("documentos", response.getCategoria(), "Categoria deve corresponder");
        assertEquals("erp-calhas", response.getOrigem(), "Origem deve corresponder");
        assertEquals("OS-123", response.getReferenciaId(), "ReferenciaId deve corresponder");
        assertFalse(response.isTemThumbnail(), "PDF nao deve ter thumbnail");
        assertNotNull(response.getUrlDownload(), "URL de download nao deve ser nula");
        assertNotNull(response.getCriadoEm(), "Data de criacao nao deve ser nula");
    }

    @Test
    @DisplayName("Deve fazer upload de imagem e gerar thumbnail")
    void deveUploadImagemEGerarThumbnail() throws IOException {
        // Criar uma imagem PNG minima valida (1x1 pixel)
        byte[] pngBytes = criarPngMinimo();
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "foto.png", "image/png", pngBytes);

        ArquivoResponse response = storageService.upload(file, "imagens", "erp-calhas", null);

        assertNotNull(response.getId(), "ID nao deve ser nulo");
        assertEquals("image/png", response.getContentType(), "Content type deve ser image/png");
        assertTrue(response.isTemThumbnail(), "Imagem deve ter thumbnail gerado");
        assertNotNull(response.getUrlThumbnail(), "URL de thumbnail nao deve ser nula");
    }

    @Test
    @DisplayName("Deve buscar arquivo existente por ID")
    void deveBuscarArquivoExistentePorId() {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "teste.txt", "text/plain", "conteudo".getBytes());
        ArquivoResponse criado = storageService.upload(file, null, null, null);

        ArquivoResponse buscado = storageService.buscar(criado.getId());

        assertEquals(criado.getId(), buscado.getId(), "IDs devem corresponder");
        assertEquals("teste.txt", buscado.getNomeOriginal(), "Nome original deve corresponder");
    }

    @Test
    @DisplayName("Deve lancar excecao ao buscar arquivo inexistente")
    void deveLancarExcecaoAoBuscarArquivoInexistente() {
        ModuloException ex = assertThrows(ModuloException.class,
                () -> storageService.buscar(999L),
                "Deve lancar ModuloException para arquivo inexistente");
        assertTrue(ex.getMessage().contains("encontrado"), "Mensagem deve indicar nao encontrado");
    }

    @Test
    @DisplayName("Deve desativar arquivo com soft delete")
    void deveDesativarArquivoComSoftDelete() {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "deletar.txt", "text/plain", "conteudo".getBytes());
        ArquivoResponse criado = storageService.upload(file, null, null, null);

        storageService.softDelete(criado.getId());

        // Verificar que agora buscar lanca excecao (ativo = false)
        ModuloException ex = assertThrows(ModuloException.class,
                () -> storageService.buscar(criado.getId()),
                "Deve lancar ModuloException apos soft delete");
        assertTrue(ex.getMessage().contains("encontrado"), "Mensagem deve indicar nao encontrado");
    }

    @Test
    @DisplayName("Deve retornar estatisticas com totais")
    void deveRetornarEstatisticasComTotais() {
        // Fazer upload de arquivos em diferentes categorias
        MockMultipartFile file1 = new MockMultipartFile(
                "arquivo", "doc1.pdf", "application/pdf", "conteudo1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "arquivo", "doc2.pdf", "application/pdf", "conteudo2-maior".getBytes());
        MockMultipartFile file3 = new MockMultipartFile(
                "arquivo", "foto.jpg", "image/jpeg", "jpeg-fake".getBytes());

        storageService.upload(file1, "documentos", null, null);
        storageService.upload(file2, "documentos", null, null);
        storageService.upload(file3, "imagens", null, null);

        StorageEstatisticasResponse stats = storageService.estatisticas();

        assertNotNull(stats, "Estatisticas nao devem ser nulas");
        assertEquals(3, stats.getTotalArquivos(), "Total de arquivos deve ser 3");
        assertTrue(stats.getTotalBytes() > 0, "Total de bytes deve ser maior que zero");
        assertNotNull(stats.getTotalFormatado(), "Total formatado nao deve ser nulo");
        assertNotNull(stats.getPorCategoria(), "Estatisticas por categoria nao devem ser nulas");
        assertTrue(stats.getPorCategoria().containsKey("documentos"), "Deve conter categoria documentos");
        assertTrue(stats.getPorCategoria().containsKey("imagens"), "Deve conter categoria imagens");
        assertEquals(2, stats.getPorCategoria().get("documentos").getQuantidade(),
                "Categoria documentos deve ter 2 arquivos");
    }

    @Test
    @DisplayName("Deve lancar excecao ao fazer upload de arquivo vazio")
    void deveLancarExcecaoAoUploadArquivoVazio() {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "vazio.txt", "text/plain", new byte[0]);

        ModuloException ex = assertThrows(ModuloException.class,
                () -> storageService.upload(file, null, null, null),
                "Deve lancar ModuloException para arquivo vazio");
        assertTrue(ex.getMessage().contains("obrigat"), "Mensagem deve indicar que arquivo e obrigatorio");
    }

    @Test
    @DisplayName("Deve lancar excecao ao buscar arquivo desativado")
    void deveLancarExcecaoAoBuscarArquivoDesativado() {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "desativado.txt", "text/plain", "conteudo".getBytes());
        ArquivoResponse criado = storageService.upload(file, null, null, null);

        storageService.softDelete(criado.getId());

        assertThrows(ModuloException.class,
                () -> storageService.buscar(criado.getId()),
                "Deve lancar excecao ao buscar arquivo desativado");
    }

    /**
     * Cria um PNG minimo valido (1x1 pixel, branco).
     */
    private byte[] criarPngMinimo() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, // 8-bit RGB
                (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, // IDAT chunk
                0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00,
                0x00, 0x00, 0x02, 0x00, 0x01, (byte) 0xE2, 0x21, (byte) 0xBC,
                0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, // IEND chunk
                0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
