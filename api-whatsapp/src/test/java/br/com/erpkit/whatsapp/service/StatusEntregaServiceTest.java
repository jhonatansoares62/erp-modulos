package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do {@link StatusEntregaService} (V7 §12) — UPDATE por wamid, rank
 * (latest-wins, sem regredir), {@code failed} terminal e skip de wamid desconhecido.
 */
@ExtendWith(MockitoExtension.class)
class StatusEntregaServiceTest {

    @Mock MensagemLogRepository repository;
    @InjectMocks StatusEntregaService service;

    private MensagemLog saida(String wamid, String statusAtual) {
        MensagemLog m = new MensagemLog(wamid, "5547984178525", Direcao.out, "text", "oi", null);
        m.setStatus(statusAtual);
        return m;
    }

    private StatusEntranteDTO status(String wamid, String status) {
        return new StatusEntranteDTO(wamid, status, "554784178525",
            Instant.ofEpochSecond(1735689600L), "CONV-1", "service", "service", true, null, null);
    }

    @Test
    @DisplayName("wamid conhecido: aplica status + conversa/pricing e salva")
    void aplica_e_salva() {
        MensagemLog alvo = saida("wamid.out.1", null);
        when(repository.findByWamid("wamid.out.1")).thenReturn(Optional.of(alvo));

        service.registrar(status("wamid.out.1", "delivered"));

        assertThat(alvo.getStatus()).isEqualTo("delivered");
        assertThat(alvo.getConversationId()).isEqualTo("CONV-1");
        assertThat(alvo.getConversaOrigem()).isEqualTo("service");
        assertThat(alvo.getCategoria()).isEqualTo("service");
        assertThat(alvo.getBillable()).isTrue();
        assertThat(alvo.getStatusEm()).isEqualTo(Instant.ofEpochSecond(1735689600L));
        verify(repository).save(alvo);
    }

    @Test
    @DisplayName("wamid desconhecido: skip silencioso, nao salva")
    void wamid_desconhecido_skip() {
        when(repository.findByWamid("wamid.x")).thenReturn(Optional.empty());
        service.registrar(status("wamid.x", "delivered"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("fora de ordem: read NAO regride para delivered (nao salva)")
    void fora_de_ordem_nao_regride() {
        MensagemLog alvo = saida("wamid.out.2", "read");
        when(repository.findByWamid("wamid.out.2")).thenReturn(Optional.of(alvo));

        service.registrar(status("wamid.out.2", "delivered"));

        assertThat(alvo.getStatus()).isEqualTo("read");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("failed sempre aplica (terminal), mesmo apos read, gravando erro")
    void failed_sempre_aplica() {
        MensagemLog alvo = saida("wamid.out.3", "read");
        when(repository.findByWamid("wamid.out.3")).thenReturn(Optional.of(alvo));
        StatusEntranteDTO s = new StatusEntranteDTO("wamid.out.3", "failed", "554784178525",
            null, null, null, null, null, 131047, "Re-engagement message");

        service.registrar(s);

        assertThat(alvo.getStatus()).isEqualTo("failed");
        assertThat(alvo.getErroCodigo()).isEqualTo("131047");
        assertThat(alvo.getErroTitulo()).isEqualTo("Re-engagement message");
        verify(repository).save(alvo);
    }
}
