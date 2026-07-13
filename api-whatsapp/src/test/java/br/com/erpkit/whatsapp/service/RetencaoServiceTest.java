package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.config.RetencaoProperties;
import br.com.erpkit.whatsapp.dto.ResultadoRetencao;
import br.com.erpkit.whatsapp.repository.MediaCacheRepository;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetencaoServiceTest {

    @Mock MensagemLogRepository mensagemRepository;
    @Mock MediaCacheRepository mediaCacheRepository;

    private final RetencaoProperties props = new RetencaoProperties();

    private RetencaoService service() {
        return new RetencaoService(mensagemRepository, mediaCacheRepository, props);
    }

    @Test
    @DisplayName("executar: anonimiza mensagens com limite = agora - N meses e purga mídia expirada")
    void executar_anonimiza_e_purga() {
        props.setMensagensMeses(24);
        when(mensagemRepository.anonimizarAntigas(any())).thenReturn(5);
        when(mediaCacheRepository.purgarExpiradas(any())).thenReturn(3);

        ResultadoRetencao r = service().executar();

        assertThat(r.mensagensAnonimizadas()).isEqualTo(5);
        assertThat(r.midiasPurgadas()).isEqualTo(3);

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(mensagemRepository).anonimizarAntigas(limite.capture());
        Instant esperado = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(24).toInstant();
        assertThat(limite.getValue()).isCloseTo(esperado, within(2, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("job desabilitado (habilitado=false) NAO toca nos repositórios")
    void job_desabilitado_nao_executa() {
        props.setHabilitado(false);

        service().jobDiario();

        verifyNoInteractions(mensagemRepository, mediaCacheRepository);
    }

    @Test
    @DisplayName("job habilitado executa a retenção")
    void job_habilitado_executa() {
        props.setHabilitado(true);
        when(mensagemRepository.anonimizarAntigas(any())).thenReturn(0);
        when(mediaCacheRepository.purgarExpiradas(any())).thenReturn(0);

        service().jobDiario();

        verify(mensagemRepository).anonimizarAntigas(any());
        verify(mediaCacheRepository).purgarExpiradas(any());
    }

    @Test
    @DisplayName("best-effort: falha ao anonimizar NAO impede o purge de mídia")
    void anonimizar_falha_nao_impede_purga() {
        when(mensagemRepository.anonimizarAntigas(any())).thenThrow(new RuntimeException("db down"));
        when(mediaCacheRepository.purgarExpiradas(any())).thenReturn(2);

        ResultadoRetencao r = service().executar();

        assertThat(r.mensagensAnonimizadas()).isZero();
        assertThat(r.midiasPurgadas()).isEqualTo(2);
    }
}
