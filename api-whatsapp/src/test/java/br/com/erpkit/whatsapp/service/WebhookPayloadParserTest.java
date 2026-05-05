package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.util.TipoMensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests do {@link WebhookPayloadParser} cobrindo o espectro de payloads do Meta:
 * text com acentos UTF-8, button_reply, list_reply, document, status, tipo desconhecido,
 * empty entry (heartbeat), multiple messages, e JSON malformado.
 *
 * <p>Pure JUnit (sem {@code @SpringBootTest}) — instancia o parser direto com
 * {@link ObjectMapper} default. Mais rapido, sem context overhead.
 */
class WebhookPayloadParserTest {

    private WebhookPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new WebhookPayloadParser(new ObjectMapper());
    }

    private byte[] fixture(String nome) throws IOException {
        try (InputStream in = new ClassPathResource("fixtures/webhook/" + nome).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    // ============================================================
    // Mensagens entrantes — 1 fixture cada cobrindo cada tipo
    // ============================================================

    @Test
    @DisplayName("text portugues — extrai conteudo + telefone normalizado (DDD 47 SC strip 9)")
    void text_portugues() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("text-portugues.json"));
        assertThat(out.mensagens()).hasSize(1);
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.wamid()).isEqualTo("wamid.HBgN.text.001");
        assertThat(m.telefone()).isEqualTo("554784178525");  // strip 9 (DDD 47)
        assertThat(m.tipo()).isEqualTo(TipoMensagem.TEXT);
        assertThat(m.conteudo()).isEqualTo("Olá, gostaria de um orçamento");
        assertThat(m.mediaId()).isNull();
    }

    @Test
    @DisplayName("button_reply — extrai id+title (DDD 11 SP preserva 9)")
    void button_reply() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("button-reply.json"));
        assertThat(out.mensagens()).hasSize(1);
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.INTERACTIVE_BUTTON);
        assertThat(m.conteudo()).isEqualTo("aprovar_1234|Aprovar");
        assertThat(m.telefone()).isEqualTo("5511987654321");  // SP — preserva 9
    }

    @Test
    @DisplayName("list_reply — extrai id+title (DDD 21 RJ preserva 9)")
    void list_reply() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("list-reply.json"));
        assertThat(out.mensagens()).hasSize(1);
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.INTERACTIVE_LIST);
        assertThat(m.conteudo()).isEqualTo("boleto|Ver boleto");
        assertThat(m.telefone()).isEqualTo("5521987654321");  // RJ — preserva 9
    }

    @Test
    @DisplayName("document — extrai filename + mediaId (DDD 31 MG strip 9)")
    void document() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("document-pdf.json"));
        assertThat(out.mensagens()).hasSize(1);
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.DOCUMENT);
        assertThat(m.conteudo()).isEqualTo("comprovante.pdf");
        assertThat(m.mediaId()).isEqualTo("media-id-12345");
        assertThat(m.telefone()).isEqualTo("553187654321");  // MG — strip 9
    }

    // ============================================================
    // Status callbacks
    // ============================================================

    @Test
    @DisplayName("status delivered — vai para statuses, nao para mensagens; telefone normalizado")
    void status_delivered() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("status-delivered.json"));
        assertThat(out.mensagens()).isEmpty();
        assertThat(out.statuses()).hasSize(1);
        assertThat(out.statuses().get(0).status()).isEqualTo("delivered");
        assertThat(out.statuses().get(0).wamid()).isEqualTo("wamid.HBgN.status.001");
        assertThat(out.statuses().get(0).telefone()).isEqualTo("554784178525");  // strip 9 SC
    }

    // ============================================================
    // Tolerancia (WEB-07 + edge cases)
    // ============================================================

    @Test
    @DisplayName("tipo desconhecido — persiste com tipo=desconhecido + conteudo null (WEB-07)")
    void tipo_desconhecido() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("tipo-desconhecido.json"));
        assertThat(out.mensagens()).hasSize(1);
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.DESCONHECIDO);
        assertThat(m.conteudo()).isNull();
        assertThat(m.mediaId()).isNull();
    }

    @Test
    @DisplayName("empty entry — listas vazias, sem erro (heartbeat do Meta)")
    void empty_entry() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("empty-entry.json"));
        assertThat(out.mensagens()).isEmpty();
        assertThat(out.statuses()).isEmpty();
    }

    @Test
    @DisplayName("multiple messages — extrai todas as 2 entries no mesmo array")
    void multiple_messages() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("multiple-messages.json"));
        assertThat(out.mensagens()).hasSize(2);
        assertThat(out.mensagens()).extracting(MensagemEntranteDTO::wamid)
            .containsExactly("wamid.multi.001", "wamid.multi.002");
        assertThat(out.mensagens()).extracting(MensagemEntranteDTO::conteudo)
            .containsExactly("primeira", "segunda");
    }

    // ============================================================
    // Erro de parse
    // ============================================================

    @Test
    @DisplayName("JSON malformado lanca IOException")
    void json_malformado() {
        assertThatThrownBy(
            () -> parser.extrair("{ invalid json".getBytes())
        ).isInstanceOf(IOException.class);
    }
}
