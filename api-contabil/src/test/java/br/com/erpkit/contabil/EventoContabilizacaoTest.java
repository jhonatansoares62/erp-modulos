package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.model.Partida;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.repository.LancamentoRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.contabil.service.ContaContabilService;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.shared.exception.ModuloException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventoContabilizacaoTest {

    @Autowired EventoService eventoService;
    @Autowired ContaContabilService contaService;
    @Autowired LancamentoRepository lancamentoRepository;
    @Autowired PartidaRepository partidaRepository;
    @Autowired ContaContabilRepository contaRepository;

    @Test
    void contabilizaVendaAvistaPix() {
        EventoContabilRequest req = venda(UUID.randomUUID().toString(), 45000, "pix", "avista");

        EventoRecebidoResponse resp = eventoService.receber(req);

        assertThat(resp.getStatus()).isEqualTo("processado");
        assertThat(resp.getLancamentoId()).isNotNull();

        List<Partida> partidas = partidaRepository.findByLancamentoId(resp.getLancamentoId());
        assertThat(partidas).hasSize(2);

        long debitos = partidas.stream().filter(p -> "D".equals(p.getTipo())).mapToLong(Partida::getValorCentavos).sum();
        long creditos = partidas.stream().filter(p -> "C".equals(p.getTipo())).mapToLong(Partida::getValorCentavos).sum();
        assertThat(debitos).isEqualTo(45000);
        assertThat(creditos).isEqualTo(45000);   // F2: balanceado

        // Regra de prioridade 30 (PIX): D Bancos/PIX (1.1.1.02) · C Receita (3.1.1.01)
        ContaContabil bancos = contaRepository.findByCodigo("1.1.1.02").orElseThrow();
        ContaContabil receita = contaRepository.findByCodigo("3.1.1.01").orElseThrow();
        Partida debito = partidas.stream().filter(p -> "D".equals(p.getTipo())).findFirst().orElseThrow();
        Partida credito = partidas.stream().filter(p -> "C".equals(p.getTipo())).findFirst().orElseThrow();
        assertThat(debito.getContaId()).isEqualTo(bancos.getId());
        assertThat(credito.getContaId()).isEqualTo(receita.getId());
    }

    @Test
    void reenvioDoMesmoEventoNaoDuplica() {
        String eventoId = UUID.randomUUID().toString();
        long lancamentosAntes = lancamentoRepository.count();

        EventoRecebidoResponse primeira = eventoService.receber(venda(eventoId, 10000, "pix", "avista"));
        EventoRecebidoResponse segunda = eventoService.receber(venda(eventoId, 10000, "pix", "avista"));

        assertThat(primeira.getLancamentoId()).isEqualTo(segunda.getLancamentoId());   // idempotência
        assertThat(lancamentoRepository.count()).isEqualTo(lancamentosAntes + 1);       // um só lançamento
    }

    @Test
    void eventoSemRoteiroViraPendencia() {
        EventoContabilRequest req = venda(UUID.randomUUID().toString(), 5000, "boleto", "avista");
        req.setTipo("evento.inexistente");

        EventoRecebidoResponse resp = eventoService.receber(req);

        assertThat(resp.getStatus()).isEqualTo("sem_regra");
        assertThat(resp.getLancamentoId()).isNull();
    }

    @Test
    void contaSinteticaNaoRecebeLancamento() {
        // 1.1.1 (Caixa e Equivalentes) é sintética — não pode receber partida (F5).
        ContaContabil sintetica = contaRepository.findByCodigo("1.1.1").orElseThrow();
        assertThatThrownBy(() -> contaService.validarRecebeLancamento(sintetica.getId()))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("sintética");
    }

    private EventoContabilRequest venda(String eventoId, long valor, String meio, String condicao) {
        EventoContabilRequest req = new EventoContabilRequest();
        req.setEventoId(eventoId);
        req.setTipo("venda.finalizada");
        req.setOrigem("erp-mudas");
        req.setDataEvento(LocalDate.of(2026, 7, 6));
        req.setValorCentavos(valor);
        EventoContabilRequest.Referencia ref = new EventoContabilRequest.Referencia();
        ref.setEntidade("venda");
        ref.setNumero("1234");
        req.setReferencia(ref);
        req.setContexto(Map.of("meioPagamento", meio, "condicao", condicao));
        return req;
    }
}
