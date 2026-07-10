package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.MetaConfigRequest;
import br.com.erpkit.whatsapp.dto.MetaConfigResponse;
import br.com.erpkit.whatsapp.model.ConfigMeta;
import br.com.erpkit.whatsapp.repository.ConfigMetaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do {@link MetaConfigService} — ponte entre a linha persistida
 * {@code config_meta} e o singleton {@link WhatsAppProperties}. Mockito puro:
 * repository mockado + WhatsAppProperties real (bean simples, sem AOP).
 */
@ExtendWith(MockitoExtension.class)
class MetaConfigServiceTest {

    @Mock
    ConfigMetaRepository repository;

    private MetaConfigService novoService(WhatsAppProperties props) {
        return new MetaConfigService(repository, props);
    }

    private ConfigMeta linha(String phone, String access, String secret, String verify) {
        ConfigMeta c = new ConfigMeta();
        c.setId(1);
        c.setPhoneNumberId(phone);
        c.setAccessToken(access);
        c.setAppSecret(secret);
        c.setVerifyToken(verify);
        c.setAtualizadoEm(Instant.parse("2026-07-10T12:00:00Z"));
        return c;
    }

    @Test
    @DisplayName("Boot: config do banco sobrepoe o seed de env var no WhatsAppProperties")
    void carregar_sobrepoe_valores_do_banco() {
        WhatsAppProperties props = new WhatsAppProperties();
        props.setPhoneNumberId("seed-phone");   // env seed
        props.setAccessToken("seed-access");
        when(repository.findById(1)).thenReturn(Optional.of(
                linha("db-phone", "db-access", "db-secret", "db-verify")));

        novoService(props).carregarDoBanco();

        assertThat(props.getPhoneNumberId()).isEqualTo("db-phone");
        assertThat(props.getAccessToken()).isEqualTo("db-access");
        assertThat(props.getAppSecret()).isEqualTo("db-secret");
        assertThat(props.getVerifyToken()).isEqualTo("db-verify");
        assertThat(props.isMetaConfigurado()).isTrue();
    }

    @Test
    @DisplayName("Boot: sem linha no banco mantem o seed de env var")
    void carregar_sem_linha_mantem_seed() {
        WhatsAppProperties props = new WhatsAppProperties();
        props.setPhoneNumberId("seed-phone");
        when(repository.findById(1)).thenReturn(Optional.empty());

        novoService(props).carregarDoBanco();

        assertThat(props.getPhoneNumberId()).isEqualTo("seed-phone");
        assertThat(props.isMetaConfigurado()).isFalse();
    }

    @Test
    @DisplayName("Boot: falha do banco nao derruba (best-effort) e mantem o seed")
    void carregar_falha_banco_nao_derruba() {
        WhatsAppProperties props = new WhatsAppProperties();
        props.setPhoneNumberId("seed-phone");
        when(repository.findById(1)).thenThrow(new RuntimeException("db down"));

        novoService(props).carregarDoBanco(); // nao lanca

        assertThat(props.getPhoneNumberId()).isEqualTo("seed-phone");
    }

    @Test
    @DisplayName("PUT: grava as 4 credenciais, aplica no bean vivo e mascara os secrets na resposta")
    void atualizar_grava_e_aplica_no_bean() {
        WhatsAppProperties props = new WhatsAppProperties();
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(repository.save(any(ConfigMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        MetaConfigResponse resp = novoService(props).atualizar(
                new MetaConfigRequest("55999", "tok-access", "sec-app", "verif"));

        // aplicado no bean vivo
        assertThat(props.getPhoneNumberId()).isEqualTo("55999");
        assertThat(props.getAccessToken()).isEqualTo("tok-access");
        assertThat(props.isMetaConfigurado()).isTrue();

        // resposta: phoneNumberId em claro, secrets so como boolean
        assertThat(resp.phoneNumberId()).isEqualTo("55999");
        assertThat(resp.accessTokenConfigurado()).isTrue();
        assertThat(resp.appSecretConfigurado()).isTrue();
        assertThat(resp.verifyTokenConfigurado()).isTrue();
        assertThat(resp.configurado()).isTrue();

        // persistiu id=1
        ArgumentCaptor<ConfigMeta> captor = ArgumentCaptor.forClass(ConfigMeta.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT parcial: campo em branco MANTEM o secret existente (nao apaga)")
    void atualizar_parcial_mantem_secret() {
        WhatsAppProperties props = new WhatsAppProperties();
        when(repository.findById(1)).thenReturn(Optional.of(
                linha("old-phone", "old-access", "old-secret", "old-verify")));
        when(repository.save(any(ConfigMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        // operador troca so o phoneNumberId; secrets vao em branco
        novoService(props).atualizar(new MetaConfigRequest("new-phone", "  ", "", null));

        ArgumentCaptor<ConfigMeta> captor = ArgumentCaptor.forClass(ConfigMeta.class);
        verify(repository).save(captor.capture());
        ConfigMeta salvo = captor.getValue();
        assertThat(salvo.getPhoneNumberId()).isEqualTo("new-phone");
        assertThat(salvo.getAccessToken()).isEqualTo("old-access");
        assertThat(salvo.getAppSecret()).isEqualTo("old-secret");
        assertThat(salvo.getVerifyToken()).isEqualTo("old-verify");
    }

    @Test
    @DisplayName("GET: reflete a config efetiva com secrets mascarados e atualizadoEm em ISO")
    void atual_mascara_e_traz_timestamp() {
        WhatsAppProperties props = new WhatsAppProperties();
        props.setPhoneNumberId("55999");
        props.setAccessToken("tok");
        props.setAppSecret("sec");
        props.setVerifyToken("ver");
        when(repository.findById(1)).thenReturn(Optional.of(
                linha("55999", "tok", "sec", "ver")));

        MetaConfigResponse resp = novoService(props).atual();

        assertThat(resp.phoneNumberId()).isEqualTo("55999");
        assertThat(resp.configurado()).isTrue();
        assertThat(resp.atualizadoEm()).isEqualTo("2026-07-10T12:00:00Z");
    }
}
