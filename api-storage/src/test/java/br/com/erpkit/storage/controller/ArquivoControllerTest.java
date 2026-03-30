package br.com.erpkit.storage.controller;

import br.com.erpkit.storage.dto.ArquivoResponse;
import br.com.erpkit.storage.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArquivoController.class)
class ArquivoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageService storageService;

    @Test
    @DisplayName("POST /api/arquivos com multipart deve retornar 201")
    void deveRetornar201AoUploadArquivo() throws Exception {
        ArquivoResponse response = criarArquivoResponse(1L);
        when(storageService.upload(any(), isNull(), isNull(), isNull())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "teste.pdf", "application/pdf", "conteudo".getBytes());

        mockMvc.perform(multipart("/api/arquivos")
                        .file(file)
                        .header("X-API-Key", "test-key-123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nomeOriginal").value("documento.pdf"));
    }

    @Test
    @DisplayName("GET /api/arquivos/{id} deve retornar 200 com arquivo")
    void deveRetornar200AoBuscarArquivo() throws Exception {
        ArquivoResponse response = criarArquivoResponse(1L);
        when(storageService.buscar(1L)).thenReturn(response);

        mockMvc.perform(get("/api/arquivos/1")
                        .header("X-API-Key", "test-key-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nomeOriginal").value("documento.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
    }

    @Test
    @DisplayName("DELETE /api/arquivos/{id} deve retornar 204")
    void deveRetornar204AoDeletarArquivo() throws Exception {
        doNothing().when(storageService).softDelete(1L);

        mockMvc.perform(delete("/api/arquivos/1")
                        .header("X-API-Key", "test-key-123"))
                .andExpect(status().isNoContent());
    }

    private ArquivoResponse criarArquivoResponse(Long id) {
        ArquivoResponse response = new ArquivoResponse();
        response.setId(id);
        response.setNomeOriginal("documento.pdf");
        response.setContentType("application/pdf");
        response.setTamanho(1024);
        response.setCategoria("documentos");
        response.setTemThumbnail(false);
        response.setUrlDownload("http://localhost:8085/api/arquivos/" + id + "/download");
        response.setCriadoEm(LocalDateTime.now());
        return response;
    }
}
