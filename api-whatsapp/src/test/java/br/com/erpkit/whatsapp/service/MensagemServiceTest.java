package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.event.MensagemPersistidaEvent;
import br.com.erpkit.whatsapp.model.Direcao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes do orquestrador {@link MensagemService} via Mockito puro (Phase 3 Wave 5).
 *
 * <p>Phase 2 usava {@link org.springframework.boot.test.context.SpringBootTest} com
 * H2 real para validar o flow de cliente_zap + mensagens_log E2E. Em Phase 3, o
 * fluxo de {@link br.com.erpkit.whatsapp.service.ClienteZapService} foi MOVIDO para
 * {@link MensagemAsyncListener} (Wave 5); restante do {@link MensagemService} virou
 * fast-path stateless: parse + idempotency + publishEvent.
 *
 * <p><b>Mockito puro</b> e suficiente agora porque o service nao tem mais cross-bean
 * call que dependa de proxy AOP do Spring (REQUIRES_NEW de ClienteZapService roda no
 * listener, em test isolado em {@link MensagemAsyncListenerTest}). Cobertura E2E DB
 * fica em {@code WebhookPersistenciaIntegrationTest} (sera atualizado em Wave 6 com
 * AsyncTestConfig + WireMock stub para ERP).
 *
 * <p>4 tests cobrem todos os branches do orquestrador:
 * <ul>
 *   <li>Mensagem nova: tentarPersistir true -&gt; publishEvent 1x com payload correto</li>
 *   <li>Mensagem duplicada: tentarPersistir false -&gt; publishEvent 0x</li>
 *   <li>Multiplas mensagens novas: publishEvent N vezes</li>
 *   <li>Status callbacks (apenas) NAO disparam MensagemPersistidaEvent (Phase 3 D-06)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    @Mock WebhookPayloadParser parser;
    @Mock IdempotencyService idempotency;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks MensagemService service;

    @Test
    @DisplayName("1 mensagem nova: tentarPersistir true -> publishEvent 1x com MensagemPersistidaEvent populado")
    void mensagem_nova_publica_event() throws Exception {
        MensagemEntranteDTO m = new MensagemEntranteDTO(
            "wamid.001", "554784178525", "5547984178525", "text", "orcamento 1234", null);
        when(parser.extrair(any())).thenReturn(new ParsedWebhook(List.of(m), List.of()));
        when(idempotency.tentarPersistir(eq("wamid.001"), anyString(), eq(Direcao.in),
                                          eq("text"), eq("orcamento 1234"), eq(null)))
            .thenReturn(true);

        service.processarWebhook(new byte[0]);

        ArgumentCaptor<MensagemPersistidaEvent> captor = ArgumentCaptor.forClass(MensagemPersistidaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        MensagemPersistidaEvent evt = captor.getValue();
        assertThat(evt.wamid()).isEqualTo("wamid.001");
        assertThat(evt.telefone()).isEqualTo("554784178525");
        assertThat(evt.telefoneWaId())
            .as("MensagemService propaga o wa_id EXATO (com 9) para o listener responder")
            .isEqualTo("5547984178525");
        assertThat(evt.tipo()).isEqualTo("text");
        assertThat(evt.conteudo()).isEqualTo("orcamento 1234");
        assertThat(evt.mediaId()).isNull();
        assertThat(evt.idClienteErp())
            .as("idClienteErp sempre null no event — listener resolve via ClienteZapService.identificar")
            .isNull();
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Mensagem duplicada: tentarPersistir false -> publishEvent 0x (Meta reenviou)")
    void mensagem_duplicada_nao_publica() throws Exception {
        MensagemEntranteDTO m = new MensagemEntranteDTO(
            "wamid.dup", "554784178525", "5547984178525", "text", "orcamento", null);
        when(parser.extrair(any())).thenReturn(new ParsedWebhook(List.of(m), List.of()));
        when(idempotency.tentarPersistir(any(), any(), any(), any(), any(), any())).thenReturn(false);

        service.processarWebhook(new byte[0]);

        verify(eventPublisher, never()).publishEvent(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Multiplas mensagens novas: publishEvent N vezes (loop do orquestrador)")
    void multiplas_mensagens_publica_n() throws Exception {
        MensagemEntranteDTO m1 = new MensagemEntranteDTO(
            "wamid.001", "554784178525", "5547984178525", "text", "a", null);
        MensagemEntranteDTO m2 = new MensagemEntranteDTO(
            "wamid.002", "554784178525", "5547984178525", "text", "b", null);
        when(parser.extrair(any())).thenReturn(new ParsedWebhook(List.of(m1, m2), List.of()));
        when(idempotency.tentarPersistir(any(), any(), any(), any(), any(), any())).thenReturn(true);

        service.processarWebhook(new byte[0]);

        verify(eventPublisher, times(2)).publishEvent(any(MensagemPersistidaEvent.class));
    }

    @Test
    @DisplayName("Apenas status callbacks: NAO publica MensagemPersistidaEvent (Phase 3 D-06)")
    void status_nao_publica() throws Exception {
        StatusEntranteDTO s = new StatusEntranteDTO("wamid.status.001", "delivered", "554784178525");
        when(parser.extrair(any())).thenReturn(new ParsedWebhook(List.of(), List.of(s)));

        service.processarWebhook(new byte[0]);

        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(idempotency);
    }
}
