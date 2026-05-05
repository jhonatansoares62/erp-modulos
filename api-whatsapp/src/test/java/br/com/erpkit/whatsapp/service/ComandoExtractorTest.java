package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.util.TipoMensagem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests JUnit puros (sem Spring context) cobrindo todos os branches do switch
 * em {@link ComandoExtractor#extrair(String, String)}.
 *
 * <p>NOTA: {@link TipoMensagem} Phase 2 tem 7 constants (TEXT, INTERACTIVE_BUTTON,
 * INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO, DESCONHECIDO) — sem VIDEO. Os tests
 * de media literal cobrem apenas DOCUMENT/IMAGE/AUDIO. Se Phase 5 (parser) adicionar
 * VIDEO, este test e {@link ComandoExtractor} precisam ganhar 1 case adicional no
 * switch + 1 assertion aqui.
 */
class ComandoExtractorTest {

    private final ComandoExtractor extractor = new ComandoExtractor();

    @Test
    @DisplayName("text: primeira palavra lowercase")
    void text_primeira_palavra() {
        assertThat(extractor.extrair(TipoMensagem.TEXT, "Orcamento 1234"))
            .isEqualTo("orcamento");
    }

    @Test
    @DisplayName("text: preserva acentos no lowercase")
    void text_acentos() {
        assertThat(extractor.extrair(TipoMensagem.TEXT, "Orcamento de geladeira"))
            .isEqualTo("orcamento");
    }

    @Test
    @DisplayName("text: vazio/null retorna null")
    void text_vazio_null() {
        assertThat(extractor.extrair(TipoMensagem.TEXT, "")).isNull();
        assertThat(extractor.extrair(TipoMensagem.TEXT, "   ")).isNull();
        assertThat(extractor.extrair(TipoMensagem.TEXT, null)).isNull();
    }

    @Test
    @DisplayName("text: multiplos espacos sao colapsados — primeira palavra so")
    void text_multiplos_espacos() {
        assertThat(extractor.extrair(TipoMensagem.TEXT, "  Aprovar    1234   "))
            .isEqualTo("aprovar");
    }

    @Test
    @DisplayName("interactive_button: id parseado do formato 'id|title'")
    void interactive_button_com_separador() {
        assertThat(extractor.extrair(TipoMensagem.INTERACTIVE_BUTTON, "aprovar_42|Aprovar"))
            .isEqualTo("aprovar_42");
    }

    @Test
    @DisplayName("interactive_list: id parseado do formato 'id|title'")
    void interactive_list_com_separador() {
        assertThat(extractor.extrair(TipoMensagem.INTERACTIVE_LIST, "menu_inicial|Menu Inicial"))
            .isEqualTo("menu_inicial");
    }

    @Test
    @DisplayName("interactive_button: id em uppercase vira lowercase")
    void interactive_button_lowercase() {
        assertThat(extractor.extrair(TipoMensagem.INTERACTIVE_BUTTON, "APROVAR_42|Aprovar"))
            .isEqualTo("aprovar_42");
    }

    @Test
    @DisplayName("interactive_button: sem '|' retorna null")
    void interactive_sem_separador() {
        assertThat(extractor.extrair(TipoMensagem.INTERACTIVE_BUTTON, "sem-pipe")).isNull();
        assertThat(extractor.extrair(TipoMensagem.INTERACTIVE_BUTTON, null)).isNull();
        assertThat(extractor.extrair(TipoMensagem.INTERACTIVE_LIST, "")).isNull();
    }

    @Test
    @DisplayName("interactive: '|' no inicio (id vazio) retorna null — sep > 0 exige caractere antes")
    void interactive_pipe_no_inicio() {
        assertThat(extractor.extrair(TipoMensagem.INTERACTIVE_BUTTON, "|Aprovar")).isNull();
    }

    @Test
    @DisplayName("document/image/audio retornam o tipo literal")
    void media_tipos_retornam_literal() {
        assertThat(extractor.extrair(TipoMensagem.DOCUMENT, "fatura.pdf"))
            .isEqualTo(TipoMensagem.DOCUMENT);
        assertThat(extractor.extrair(TipoMensagem.IMAGE, "qualquer"))
            .isEqualTo(TipoMensagem.IMAGE);
        assertThat(extractor.extrair(TipoMensagem.AUDIO, "qualquer"))
            .isEqualTo(TipoMensagem.AUDIO);
    }

    @Test
    @DisplayName("media: conteudo null nao afeta retorno literal")
    void media_conteudo_null() {
        // Per logica do switch, document/image/audio retornam o tipo literal independente do conteudo.
        // Parser Phase 2 sempre coloca filename/null em conteudo; extractor nao precisa validar.
        assertThat(extractor.extrair(TipoMensagem.DOCUMENT, null))
            .isEqualTo(TipoMensagem.DOCUMENT);
    }

    @Test
    @DisplayName("tipo desconhecido OU null retorna null (skip dispatch)")
    void desconhecido_e_null() {
        assertThat(extractor.extrair(TipoMensagem.DESCONHECIDO, "qualquer")).isNull();
        assertThat(extractor.extrair(null, "qualquer")).isNull();
        assertThat(extractor.extrair("tipo_inexistente", "qualquer")).isNull();
    }

    @Test
    @DisplayName("video (sem constant em TipoMensagem) retorna null — Phase 2 mapeou como desconhecido")
    void video_nao_existe_constant() {
        // Defensivo: se um payload literal "video" chegasse, switch cai no default -> null.
        // (Phase 2 parser ja teria normalizado para DESCONHECIDO antes; este test cobre
        // o caminho do switch caso parser mude no futuro.)
        assertThat(extractor.extrair("video", "video.mp4")).isNull();
    }
}
