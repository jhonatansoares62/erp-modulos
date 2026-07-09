package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.RegraCreateDTO;
import br.com.erpkit.contabil.dto.RegraPartidaDTO;
import br.com.erpkit.contabil.dto.RegraResponse;
import br.com.erpkit.contabil.model.RegraLancamento;
import br.com.erpkit.contabil.service.RegraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.erpkit.contabil.support.AbstractPostgresIT;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RegraPanelTest extends AbstractPostgresIT {

    @Autowired RegraService regraService;

    @Test
    void listaTodasComPartidasResolvidasEDesativa() {
        int antes = regraService.listarTodas().size();   // seed já traz roteiros

        RegraLancamento a = regraService.criar(regra("evento.a"));
        regraService.criar(regra("evento.b"));

        List<RegraResponse> todas = regraService.listarTodas();
        assertThat(todas).hasSize(antes + 2);

        // A regra criada tem as partidas resolvidas (código + nome, não null).
        RegraResponse criada = todas.stream().filter(r -> "evento.a".equals(r.getEventoTipo())).findFirst().orElseThrow();
        assertThat(criada.getPartidas()).hasSize(2);
        assertThat(criada.getPartidas()).allSatisfy(p -> {
            assertThat(p.getContaCodigo()).isNotNull();
            assertThat(p.getContaNome()).isNotNull();
        });
        assertThat(criada.getPartidas()).extracting("tipo").containsExactlyInAnyOrder("D", "C");

        regraService.desativar(a.getId());
        assertThat(regraService.listarTodas()).hasSize(antes + 1);
        assertThat(regraService.listarTodas()).noneMatch(r -> "evento.a".equals(r.getEventoTipo()));
    }

    private RegraCreateDTO regra(String eventoTipo) {
        RegraCreateDTO dto = new RegraCreateDTO();
        dto.setEventoTipo(eventoTipo);
        dto.setHistoricoTemplate("Teste " + eventoTipo);
        dto.setPartidas(List.of(partida("D", "1.1.1.01"), partida("C", "3.1.1.01")));
        return dto;
    }

    private RegraPartidaDTO partida(String tipo, String contaCodigo) {
        RegraPartidaDTO p = new RegraPartidaDTO();
        p.setTipo(tipo);
        p.setContaModo("constante");
        p.setContaCodigo(contaCodigo);
        p.setBase("valor_total");
        return p;
    }
}
