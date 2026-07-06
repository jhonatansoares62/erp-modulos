package br.com.erpkit.contabil;

import br.com.erpkit.contabil.dto.ContaCreateDTO;
import br.com.erpkit.contabil.dto.ContaUpdateDTO;
import br.com.erpkit.contabil.dto.EventoContabilRequest;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.service.ContaContabilService;
import br.com.erpkit.contabil.service.EventoService;
import br.com.erpkit.shared.exception.ModuloException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContaCrudTest {

    @Autowired ContaContabilService contaService;
    @Autowired ContaContabilRepository contaRepository;
    @Autowired EventoService eventoService;

    @Test
    void atualizaNomeDaConta() {
        ContaContabil conta = contaService.criar(novaAnalitica("9.9.9.91", "Conta Teste"));

        ContaUpdateDTO dto = new ContaUpdateDTO();
        dto.setNome("Conta Renomeada");
        dto.setNatureza("C");
        dto.setRetificadora(false);

        ContaContabil atualizada = contaService.atualizar(conta.getId(), dto);

        assertThat(atualizada.getNome()).isEqualTo("Conta Renomeada");
        assertThat(contaRepository.findById(conta.getId()).orElseThrow().getNome()).isEqualTo("Conta Renomeada");
    }

    @Test
    void softDeleteContaSemPartida() {
        ContaContabil conta = contaService.criar(novaAnalitica("9.9.9.92", "Conta Sem Uso"));

        contaService.softDelete(conta.getId());

        assertThat(contaRepository.findById(conta.getId()).orElseThrow().isAtivo()).isFalse();
    }

    @Test
    void softDeleteBloqueadoQuandoContaTemPartida() {
        // Venda à vista PIX credita a Receita 3.1.1.01 (regra de prioridade 30).
        eventoService.receber(vendaAvistaPix(UUID.randomUUID().toString(), 45000));
        ContaContabil receita = contaRepository.findByCodigo("3.1.1.01").orElseThrow();

        assertThatThrownBy(() -> contaService.softDelete(receita.getId()))
                .isInstanceOf(ModuloException.class)
                .hasMessageContaining("lançamentos");
    }

    private ContaCreateDTO novaAnalitica(String codigo, String nome) {
        ContaCreateDTO dto = new ContaCreateDTO();
        dto.setCodigo(codigo);
        dto.setNome(nome);
        dto.setTipo("analitica");
        dto.setNatureza("D");
        dto.setGrupo("receita");
        dto.setRetificadora(false);
        return dto;
    }

    private EventoContabilRequest vendaAvistaPix(String eventoId, long valor) {
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
        req.setContexto(Map.of("meioPagamento", "pix", "condicao", "avista"));
        return req;
    }
}
