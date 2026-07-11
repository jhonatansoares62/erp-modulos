package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ComandoCallbackDTO;
import br.com.erpkit.whatsapp.dto.DesfechoCallbackDTO;
import br.com.erpkit.whatsapp.dto.MetaMediaResultado;
import br.com.erpkit.whatsapp.event.MensagemPersistidaEvent;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.util.TipoMensagem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests Mockito puro do {@link MensagemAsyncListener} (Phase 3 Wave 5).
 *
 * <p>Cobre os 5 branches principais do orquestrador async:
 * <ol>
 *   <li>{@link #media_id_null_pula_step_1}: text event sem mediaId — InOrder verifica
 *       identificar -&gt; atualizar -&gt; extrair -&gt; despachar (sem chamada a metaMediaClient).</li>
 *   <li>{@link #media_baixada_primeiro_callback_com_base64}: document event com mediaId —
 *       InOrder verifica metaMediaClient PRIMEIRO + ArgumentCaptor confirma payload com
 *       mediaBase64 = "AQID" (base64 de [1,2,3]) + mimeType + filename corretos.</li>
 *   <li>{@link #media_404_callback_sem_base64}: metaMediaClient.baixar retorna
 *       Optional.empty — callback chamado SEM mediaBase64 (null).</li>
 *   <li>{@link #comando_null_callback_nao_chamado}: comandoExtractor retorna null
 *       (tipo desconhecido) — verifyNoInteractions(erpCallbackClient).</li>
 *   <li>{@link #identificar_lanca_return_early}: clienteZapService.identificar lanca —
 *       listener NAO propaga; atualizar/extrair/despachar NAO sao chamados.</li>
 * </ol>
 *
 * <p>Bonus: {@link #atualizar_lanca_return_early}, {@link #despachar_lanca_NAO_propaga},
 * {@link #media_baixar_lanca_continua_sem_base64} cobrem outros branches de defesa.
 *
 * <p>InOrder valida ordem das chamadas — invariant critico do D-01 (media DEVE ser
 * baixada primeiro porque URL Meta expira em 5min, PITFALLS C-08).
 */
@ExtendWith(MockitoExtension.class)
class MensagemAsyncListenerTest {

    @Mock MetaMediaClient metaMediaClient;
    @Mock ClienteZapService clienteZapService;
    @Mock ComandoExtractor comandoExtractor;
    @Mock ErpCallbackClient erpCallbackClient;
    @Mock MensagemLogService mensagemLogService;

    @InjectMocks MensagemAsyncListener listener;

    // telefone NORMALIZADO (sem 9o digito, DDD 47) usado internamente vs
    // telefoneWaId = wa_id EXATO do Meta (com 9o digito) usado no callback/resposta.
    private static final String TEL_NORMALIZADO = "554784178525";
    private static final String TEL_WA_ID = "5547984178525";
    private static final Instant TS = Instant.ofEpochSecond(1735689600L);

    private MensagemPersistidaEvent eventoText() {
        return new MensagemPersistidaEvent(
            "wamid.001", TEL_NORMALIZADO, TEL_WA_ID, TipoMensagem.TEXT, "orcamento 1234", null, null, TS);
    }

    private MensagemPersistidaEvent eventoDocumento() {
        return new MensagemPersistidaEvent(
            "wamid.002", TEL_NORMALIZADO, TEL_WA_ID, TipoMensagem.DOCUMENT, "fatura.pdf",
            "MEDIA-ID-001", null, TS);
    }

    private ClienteZap clienteMock(Long idClienteErp) {
        ClienteZap c = new ClienteZap();
        c.setTelefone("554784178525");
        c.setIdClienteErp(idClienteErp);
        return c;
    }

    @Test
    @DisplayName("mediaId=null: pula step 1; identificar -> atualizar -> extrair -> despachar em ordem")
    void media_id_null_pula_step_1() {
        when(clienteZapService.identificar("554784178525")).thenReturn(clienteMock(42L));
        when(comandoExtractor.extrair(TipoMensagem.TEXT, "orcamento 1234")).thenReturn("orcamento");

        listener.aoMensagemPersistida(eventoText());

        InOrder order = inOrder(clienteZapService, comandoExtractor, erpCallbackClient);
        order.verify(clienteZapService).identificar("554784178525");
        order.verify(clienteZapService).atualizarUltimaMensagemEm("554784178525");
        order.verify(comandoExtractor).extrair(TipoMensagem.TEXT, "orcamento 1234");
        order.verify(erpCallbackClient).despachar(any(ComandoCallbackDTO.class));
        verifyNoInteractions(metaMediaClient);
        // V7 §12: linha de entrada enriquecida com id_cliente_erp + comando + evento_em
        verify(mensagemLogService).enriquecerEntrada("wamid.001", 42L, "orcamento", TS);
    }

    @Test
    @DisplayName("mediaId presente happy: media baixada PRIMEIRO; callback com mediaBase64+mime+filename populados")
    void media_baixada_primeiro_callback_com_base64() {
        when(metaMediaClient.baixar("MEDIA-ID-001"))
            .thenReturn(Optional.of(new MetaMediaResultado(
                new byte[]{1, 2, 3}, "application/pdf", "fatura.pdf")));
        when(clienteZapService.identificar(any())).thenReturn(clienteMock(42L));
        when(comandoExtractor.extrair(TipoMensagem.DOCUMENT, "fatura.pdf"))
            .thenReturn(TipoMensagem.DOCUMENT);

        listener.aoMensagemPersistida(eventoDocumento());

        // InOrder: media DEVE ser primeiro (TTL 5min)
        InOrder order = inOrder(metaMediaClient, clienteZapService, comandoExtractor, erpCallbackClient);
        order.verify(metaMediaClient).baixar("MEDIA-ID-001");
        order.verify(clienteZapService).identificar(any());
        order.verify(clienteZapService).atualizarUltimaMensagemEm(any());
        order.verify(comandoExtractor).extrair(any(), any());

        ArgumentCaptor<ComandoCallbackDTO> captor = ArgumentCaptor.forClass(ComandoCallbackDTO.class);
        order.verify(erpCallbackClient).despachar(captor.capture());

        ComandoCallbackDTO dto = captor.getValue();
        assertThat(dto.mediaBase64())
            .as("Base64 de [1,2,3] = AQID")
            .isEqualTo("AQID");
        assertThat(dto.mediaMimeType()).isEqualTo("application/pdf");
        assertThat(dto.mediaFilename()).isEqualTo("fatura.pdf");
        assertThat(dto.idCliente()).isEqualTo(42L);
        assertThat(dto.telefone())
            .as("callback carrega o wa_id EXATO do Meta (com 9), NAO o normalizado")
            .isEqualTo(TEL_WA_ID);
        assertThat(dto.comando()).isEqualTo(TipoMensagem.DOCUMENT);
        assertThat(dto.payload()).isEqualTo("fatura.pdf");
    }

    @Test
    @DisplayName("Media 404 (Optional.empty): callback chamado SEM mediaBase64 (mensagem persistida sem bytes)")
    void media_404_callback_sem_base64() {
        when(metaMediaClient.baixar("MEDIA-ID-001")).thenReturn(Optional.empty());
        when(clienteZapService.identificar(any())).thenReturn(clienteMock(42L));
        when(comandoExtractor.extrair(any(), any())).thenReturn(TipoMensagem.DOCUMENT);

        listener.aoMensagemPersistida(eventoDocumento());

        ArgumentCaptor<ComandoCallbackDTO> captor = ArgumentCaptor.forClass(ComandoCallbackDTO.class);
        verify(erpCallbackClient).despachar(captor.capture());
        assertThat(captor.getValue().mediaBase64()).isNull();
        assertThat(captor.getValue().mediaMimeType()).isNull();
        assertThat(captor.getValue().mediaFilename()).isNull();
    }

    @Test
    @DisplayName("metaMediaClient.baixar lanca: log.warn + prossegue (callback sem mediaBase64)")
    void media_baixar_lanca_continua_sem_base64() {
        when(metaMediaClient.baixar("MEDIA-ID-001"))
            .thenThrow(new RuntimeException("connection reset"));
        when(clienteZapService.identificar(any())).thenReturn(clienteMock(42L));
        when(comandoExtractor.extrair(any(), any())).thenReturn(TipoMensagem.DOCUMENT);

        assertThatNoException().isThrownBy(() -> listener.aoMensagemPersistida(eventoDocumento()));

        ArgumentCaptor<ComandoCallbackDTO> captor = ArgumentCaptor.forClass(ComandoCallbackDTO.class);
        verify(erpCallbackClient).despachar(captor.capture());
        assertThat(captor.getValue().mediaBase64())
            .as("media falhou — prossegue sem bytes (D-04)")
            .isNull();
    }

    @Test
    @DisplayName("Comando null (tipo desconhecido): identificar+atualizar chamados; despachar NAO chamado")
    void comando_null_callback_nao_chamado() {
        when(clienteZapService.identificar(any())).thenReturn(clienteMock(42L));
        when(comandoExtractor.extrair(any(), any())).thenReturn(null);

        listener.aoMensagemPersistida(eventoText());

        verify(clienteZapService).identificar(any());
        verify(clienteZapService).atualizarUltimaMensagemEm(any());
        verifyNoInteractions(erpCallbackClient);
        // V7 §12: enriquece mesmo sem comando (comando=null nao apaga; evento_em segue)
        verify(mensagemLogService).enriquecerEntrada("wamid.001", 42L, null, TS);
    }

    @Test
    @DisplayName("identificar lanca: return early — atualizar/extrair/despachar NAO chamados (NAO propaga)")
    void identificar_lanca_return_early() {
        when(clienteZapService.identificar(any())).thenThrow(new RuntimeException("DB down"));

        assertThatNoException().isThrownBy(() -> listener.aoMensagemPersistida(eventoText()));

        verify(clienteZapService).identificar(any());
        // atualizar NAO chamado
        verifyNoMoreInteractions(clienteZapService);
        verifyNoInteractions(comandoExtractor, erpCallbackClient);
    }

    @Test
    @DisplayName("atualizarUltimaMensagemEm lanca: return early — extrair/despachar NAO chamados")
    void atualizar_lanca_return_early() {
        when(clienteZapService.identificar(any())).thenReturn(clienteMock(42L));
        // doThrow porque atualizarUltimaMensagemEm retorna boolean (nao void)
        when(clienteZapService.atualizarUltimaMensagemEm(any()))
            .thenThrow(new RuntimeException("DB lock timeout"));

        assertThatNoException().isThrownBy(() -> listener.aoMensagemPersistida(eventoText()));

        verify(clienteZapService).identificar(any());
        verify(clienteZapService).atualizarUltimaMensagemEm(any());
        verifyNoInteractions(comandoExtractor, erpCallbackClient);
    }

    @Test
    @DisplayName("erpCallbackClient.despachar lanca (apos fallback): listener NAO propaga (catch defensivo)")
    void despachar_lanca_NAO_propaga() {
        when(clienteZapService.identificar(any())).thenReturn(clienteMock(42L));
        when(comandoExtractor.extrair(any(), any())).thenReturn("orcamento");
        // RuntimeException defensivo — Resilience4j fallback ja deveria ter engolido,
        // mas o catch-all do listener garante invariant: async nao propaga.
        org.mockito.Mockito.doThrow(new RuntimeException("OOM no base64"))
            .when(erpCallbackClient).despachar(any());

        assertThatNoException().isThrownBy(() -> listener.aoMensagemPersistida(eventoText()));

        verify(erpCallbackClient).despachar(any());
    }

    @Test
    @DisplayName("desfecho do callback: persiste resultado via mensagemLogService (§12 #6)")
    void desfecho_persistido() {
        when(clienteZapService.identificar(any())).thenReturn(clienteMock(42L));
        when(comandoExtractor.extrair(any(), any())).thenReturn("oi");
        when(erpCallbackClient.despachar(any())).thenReturn(new DesfechoCallbackDTO("respondido"));

        listener.aoMensagemPersistida(eventoText());

        verify(mensagemLogService).registrarDesfecho("wamid.001", "respondido");
    }
}
