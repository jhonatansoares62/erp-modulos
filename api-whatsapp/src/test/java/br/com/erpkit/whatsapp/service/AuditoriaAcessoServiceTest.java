package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.AuditoriaAcesso;
import br.com.erpkit.whatsapp.repository.AuditoriaAcessoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Testes da regra de dedup/registro da trilha de auditoria (LGPD item 3).
 * Mockito puro: repository mockado + dedup in-memory real do service.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaAcessoServiceTest {

    @Mock
    AuditoriaAcessoRepository repository;

    @InjectMocks
    AuditoriaAcessoService service;

    @Test
    @DisplayName("abriu_chat repetido (polling) na janela = 1 registro (dedup por atendente+telefone)")
    void abriu_chat_deduplica_na_janela() {
        service.registrar("ana@clinica", AuditoriaAcessoService.ABRIU_CHAT, "5546920009012");
        service.registrar("ana@clinica", AuditoriaAcessoService.ABRIU_CHAT, "5546920009012");
        service.registrar("ana@clinica", AuditoriaAcessoService.ABRIU_CHAT, "5546920009012");

        verify(repository, times(1)).save(org.mockito.ArgumentMatchers.any(AuditoriaAcesso.class));
    }

    @Test
    @DisplayName("abriu_chat de telefones diferentes NAO deduplica (chave inclui o telefone)")
    void abriu_chat_telefones_diferentes_registram() {
        service.registrar("ana@clinica", AuditoriaAcessoService.ABRIU_CHAT, "5546920009012");
        service.registrar("ana@clinica", AuditoriaAcessoService.ABRIU_CHAT, "5519982583529");

        verify(repository, times(2)).save(org.mockito.ArgumentMatchers.any(AuditoriaAcesso.class));
    }

    @Test
    @DisplayName("acoes de clique (assumir/encerrar) registram SEMPRE — sem dedup")
    void acoes_de_clique_registram_sempre() {
        service.registrar("ana@clinica", "assumiu", "5546920009012");
        service.registrar("ana@clinica", "assumiu", "5546920009012");
        service.registrar("ana@clinica", "encerrou", "5546920009012");

        verify(repository, times(3)).save(org.mockito.ArgumentMatchers.any(AuditoriaAcesso.class));
    }

    @Test
    @DisplayName("acao ou telefone null = ignora (nao grava lixo)")
    void acao_ou_telefone_null_ignora() {
        service.registrar("ana@clinica", null, "5546920009012");
        service.registrar("ana@clinica", AuditoriaAcessoService.ABRIU_CHAT, null);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(AuditoriaAcesso.class));
    }

    @Test
    @DisplayName("email null (acesso via X-API-Key/ERP) ainda registra")
    void email_null_registra() {
        service.registrar(null, AuditoriaAcessoService.ABRIU_CHAT, "5546920009012");

        verify(repository, times(1)).save(org.mockito.ArgumentMatchers.any(AuditoriaAcesso.class));
    }
}
