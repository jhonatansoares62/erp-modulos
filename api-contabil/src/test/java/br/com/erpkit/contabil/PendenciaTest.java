package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.dto.RegraCreateDTO;
import br.com.erpkit.contabil.dto.RegraPartidaDTO;
import br.com.erpkit.contabil.model.Partida;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.contabil.service.PendenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PendenciaTest {

    @Autowired EventoService eventoService;
    @Autowired PendenciaService pendenciaService;
    @Autowired PartidaRepository partidaRepository;

    @Test
    void salvarComoRegraReprocessaEventoPendente() {
        // Evento de um tipo sem roteiro no seed → vira pendência.
        String eventoId = UUID.randomUUID().toString();
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(eventoId);
        req.setTipo("servico.prestado");
        req.setOrigem("erp-odonto");
        req.setDataEvento(LocalDate.of(2026, 7, 6));
        req.setValorCentavos(20000);
        req.setContexto(Map.of());

        EventoRecebidoResponse recebido = eventoService.receber(req);
        assertThat(recebido.getStatus()).isEqualTo("sem_regra");
        assertThat(pendenciaService.listar()).extracting("eventoId").contains(eventoId);

        // Contador classifica: D Caixa (1.1.1.01) · C Receita (3.1.1.01).
        RegraCreateDTO dto = new RegraCreateDTO();
        dto.setHistoricoTemplate("Serviço prestado");
        dto.setPartidas(List.of(
                partida("D", "1.1.1.01"),
                partida("C", "3.1.1.01")));

        EventoRecebidoResponse resolvido = pendenciaService.salvarComoRegra(eventoId, dto);

        assertThat(resolvido.getStatus()).isEqualTo("processado");
        assertThat(resolvido.getLancamentoId()).isNotNull();
        assertThat(pendenciaService.listar()).extracting("eventoId").doesNotContain(eventoId);

        List<Partida> partidas = partidaRepository.findByLancamentoId(resolvido.getLancamentoId());
        assertThat(partidas).hasSize(2);
        long debitos = partidas.stream().filter(p -> "D".equals(p.getTipo())).mapToLong(Partida::getValorCentavos).sum();
        long creditos = partidas.stream().filter(p -> "C".equals(p.getTipo())).mapToLong(Partida::getValorCentavos).sum();
        assertThat(debitos).isEqualTo(20000);
        assertThat(creditos).isEqualTo(20000);
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
