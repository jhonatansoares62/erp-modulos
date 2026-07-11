package br.com.erpkit.whatsapp.service;

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
 * Testes do {@link MensagemLogService} (V7 §12) — enriquecimento por wamid
 * (id_cliente_erp + comando + evento_em), COALESCE manual (null nao apaga),
 * skip de wamid desconhecido.
 */
@ExtendWith(MockitoExtension.class)
class MensagemLogServiceTest {

    @Mock MensagemLogRepository repository;
    @InjectMocks MensagemLogService service;

    private MensagemLog entrada(String wamid) {
        return new MensagemLog(wamid, "554784178525", Direcao.in, "text", "oi", null);
    }

    @Test
    @DisplayName("wamid conhecido: seta id_cliente_erp + comando + evento_em e salva")
    void enriquece_e_salva() {
        MensagemLog alvo = entrada("wamid.in.1");
        when(repository.findByWamid("wamid.in.1")).thenReturn(Optional.of(alvo));
        Instant ts = Instant.ofEpochSecond(1735689600L);

        service.enriquecerEntrada("wamid.in.1", 42L, "orcamento", ts);

        assertThat(alvo.getIdClienteErp()).isEqualTo(42L);
        assertThat(alvo.getComando()).isEqualTo("orcamento");
        assertThat(alvo.getEventoEm()).isEqualTo(ts);
        verify(repository).save(alvo);
    }

    @Test
    @DisplayName("campos null nao sobrescrevem valores existentes (COALESCE manual)")
    void null_nao_apaga() {
        MensagemLog alvo = entrada("wamid.in.2");
        alvo.setIdClienteErp(7L);
        alvo.setComando("consulta");
        when(repository.findByWamid("wamid.in.2")).thenReturn(Optional.of(alvo));

        service.enriquecerEntrada("wamid.in.2", null, null, null);

        assertThat(alvo.getIdClienteErp()).isEqualTo(7L);
        assertThat(alvo.getComando()).isEqualTo("consulta");
    }

    @Test
    @DisplayName("wamid desconhecido: no-op, nao salva")
    void wamid_desconhecido_skip() {
        when(repository.findByWamid("wamid.x")).thenReturn(Optional.empty());
        service.enriquecerEntrada("wamid.x", 42L, "orcamento", null);
        verify(repository, never()).save(any());
    }
}
