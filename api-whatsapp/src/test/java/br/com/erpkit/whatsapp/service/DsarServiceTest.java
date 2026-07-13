package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ExportacaoTitularResponse;
import br.com.erpkit.whatsapp.dto.ResultadoEsquecimento;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.repository.EstadoConversaRepository;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsarServiceTest {

    @Mock MensagemLogRepository mensagemRepository;
    @Mock ClienteZapRepository clienteRepository;
    @Mock EstadoConversaRepository estadoConversaRepository;

    @InjectMocks DsarService service;

    @Test
    @DisplayName("exportar: traz as mensagens (conteúdo decifrado pelo getter) + id do ERP")
    void exportar_traz_mensagens_e_id_erp() {
        when(clienteRepository.findByTelefone(anyString()))
                .thenReturn(Optional.of(new ClienteZap("5546920009012", 42L)));
        when(mensagemRepository.findByTelefoneOrderByCriadoEmAsc(anyString())).thenReturn(List.of(
                new MensagemLog("w1", "5546920009012", Direcao.in, "text", "oi", null),
                new MensagemLog("w2", "5546920009012", Direcao.out, "text", "olá", null)));

        ExportacaoTitularResponse resp = service.exportar("5546920009012");

        assertThat(resp.idClienteErp()).isEqualTo(42L);
        assertThat(resp.mensagens()).hasSize(2);
        assertThat(resp.mensagens().get(0).direcao()).isEqualTo("in");
        assertThat(resp.mensagens().get(0).conteudo()).isEqualTo("oi");
        assertThat(resp.mensagens().get(1).conteudo()).isEqualTo("olá");
    }

    @Test
    @DisplayName("esquecer: anonimiza mensagens + remove vínculos (cliente/estado)")
    void esquecer_anonimiza_e_remove() {
        when(mensagemRepository.anonimizarPorTelefone(anyString())).thenReturn(3);
        when(clienteRepository.deletarPorTelefone(anyString())).thenReturn(1);
        when(estadoConversaRepository.deletarPorTelefone(anyString())).thenReturn(1);

        ResultadoEsquecimento r = service.esquecer("5546920009012");

        assertThat(r.mensagensAnonimizadas()).isEqualTo(3);
        assertThat(r.clienteRemovido()).isTrue();
        assertThat(r.estadoRemovido()).isTrue();
    }

    @Test
    @DisplayName("esquecer: sem vínculos (0 linhas) reflete false, mas não quebra")
    void esquecer_sem_vinculos() {
        when(mensagemRepository.anonimizarPorTelefone(anyString())).thenReturn(0);
        when(clienteRepository.deletarPorTelefone(anyString())).thenReturn(0);
        when(estadoConversaRepository.deletarPorTelefone(anyString())).thenReturn(0);

        ResultadoEsquecimento r = service.esquecer("5500000000000");

        assertThat(r.mensagensAnonimizadas()).isZero();
        assertThat(r.clienteRemovido()).isFalse();
        assertThat(r.estadoRemovido()).isFalse();
    }
}
