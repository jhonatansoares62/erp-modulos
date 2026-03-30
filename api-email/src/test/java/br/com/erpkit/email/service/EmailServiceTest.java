package br.com.erpkit.email.service;

import br.com.erpkit.email.dto.ContaEmailCreateDTO;
import br.com.erpkit.email.dto.ContaEmailResponse;
import br.com.erpkit.email.dto.EmailCreateDTO;
import br.com.erpkit.email.dto.EmailResponse;
import br.com.erpkit.email.repository.ContaEmailRepository;
import br.com.erpkit.email.repository.EmailRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private ContaEmailService contaEmailService;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private ContaEmailRepository contaEmailRepository;

    private ContaEmailResponse contaPadrao;

    @BeforeEach
    void setUp() {
        emailRepository.deleteAll();
        contaEmailRepository.deleteAll();

        // Criar conta padrao para testes
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setPreset("gmail");
        dto.setUsername("test@gmail.com");
        dto.setPassword("app-password");
        dto.setRemetente("test@gmail.com");
        dto.setPadrao(true);

        contaPadrao = contaEmailService.criar(dto);
    }

    @Test
    @DisplayName("Deve criar email e enfileirar com status pendente")
    void deveCriarEmailComStatusPendente() {
        EmailCreateDTO dto = criarEmailDto();

        EmailResponse response = emailService.criar(dto);

        assertNotNull(response.getId(), "ID do email nao deve ser nulo");
        assertEquals("pendente", response.getStatus(), "Status deve ser pendente");
        assertEquals("dest@example.com", response.getDestinatario(), "Destinatario deve corresponder");
        assertEquals("Assunto teste", response.getAssunto(), "Assunto deve corresponder");
        assertEquals(0, response.getTentativas(), "Tentativas devem ser zero");
        assertEquals(contaPadrao.getId(), response.getContaId(), "ContaId deve ser da conta padrao");
    }

    @Test
    @DisplayName("Deve criar email com contaId especifica")
    void deveCriarEmailComContaIdEspecifica() {
        EmailCreateDTO dto = criarEmailDto();
        dto.setContaId(contaPadrao.getId());

        EmailResponse response = emailService.criar(dto);

        assertEquals(contaPadrao.getId(), response.getContaId(), "ContaId deve ser a informada");
    }

    @Test
    @DisplayName("Deve buscar email por id")
    void deveBuscarEmailPorId() {
        EmailCreateDTO dto = criarEmailDto();
        EmailResponse criado = emailService.criar(dto);

        EmailResponse buscado = emailService.buscar(criado.getId());

        assertEquals(criado.getId(), buscado.getId(), "IDs devem corresponder");
        assertEquals("dest@example.com", buscado.getDestinatario(), "Destinatario deve corresponder");
    }

    @Test
    @DisplayName("Deve lancar excecao ao buscar email inexistente")
    void deveLancarExcecaoAoBuscarEmailInexistente() {
        ModuloException ex = assertThrows(ModuloException.class,
                () -> emailService.buscar(999L),
                "Deve lancar ModuloException para email inexistente");
        assertTrue(ex.getMessage().contains("encontrado"), "Mensagem deve indicar nao encontrado");
    }

    @Test
    @DisplayName("Deve reenviar email resetando tentativas")
    void deveReenviarEmailResetandoTentativas() {
        EmailCreateDTO dto = criarEmailDto();
        EmailResponse criado = emailService.criar(dto);

        // Simular que o email falhou, alterando no banco
        var email = emailRepository.findById(criado.getId()).orElseThrow();
        email.setStatus("falha");
        email.setTentativas(3);
        email.setErroMensagem("Connection refused");
        emailRepository.save(email);

        EmailResponse reenviado = emailService.reenviar(criado.getId());

        assertEquals("pendente", reenviado.getStatus(), "Status deve voltar para pendente");
        assertEquals(0, reenviado.getTentativas(), "Tentativas devem ser resetadas para zero");
    }

    @Test
    @DisplayName("Deve lancar excecao ao reenviar email ja enviado")
    void deveLancarExcecaoAoReenviarEmailJaEnviado() {
        EmailCreateDTO dto = criarEmailDto();
        EmailResponse criado = emailService.criar(dto);

        var email = emailRepository.findById(criado.getId()).orElseThrow();
        email.setStatus("enviado");
        emailRepository.save(email);

        ModuloException ex = assertThrows(ModuloException.class,
                () -> emailService.reenviar(criado.getId()),
                "Deve lancar ModuloException para email ja enviado");
        assertTrue(ex.getMessage().contains("enviado"), "Mensagem deve mencionar que ja foi enviado");
    }

    @Test
    @DisplayName("Deve cancelar email pendente")
    void deveCancelarEmailPendente() {
        EmailCreateDTO dto = criarEmailDto();
        EmailResponse criado = emailService.criar(dto);

        EmailResponse cancelado = emailService.cancelar(criado.getId());

        assertEquals("cancelado", cancelado.getStatus(), "Status deve ser cancelado");
    }

    @Test
    @DisplayName("Deve lancar excecao ao cancelar email ja enviado")
    void deveLancarExcecaoAoCancelarEmailJaEnviado() {
        EmailCreateDTO dto = criarEmailDto();
        EmailResponse criado = emailService.criar(dto);

        var email = emailRepository.findById(criado.getId()).orElseThrow();
        email.setStatus("enviado");
        emailRepository.save(email);

        ModuloException ex = assertThrows(ModuloException.class,
                () -> emailService.cancelar(criado.getId()),
                "Deve lancar ModuloException para email ja enviado");
        assertTrue(ex.getMessage().contains("enviado"), "Mensagem deve mencionar que ja foi enviado");
    }

    @Test
    @DisplayName("Deve retornar estatisticas com contagem por status")
    void deveRetornarEstatisticasComContagemPorStatus() {
        // Criar emails com diferentes status
        EmailCreateDTO dto1 = criarEmailDto();
        emailService.criar(dto1); // pendente

        EmailCreateDTO dto2 = criarEmailDto();
        dto2.setDestinatario("outro@example.com");
        emailService.criar(dto2); // pendente

        EmailCreateDTO dto3 = criarEmailDto();
        dto3.setDestinatario("mais@example.com");
        EmailResponse resp3 = emailService.criar(dto3);
        emailService.cancelar(resp3.getId()); // cancelado

        Map<String, Long> stats = emailService.estatisticas();

        assertNotNull(stats, "Estatisticas nao devem ser nulas");
        assertTrue(stats.containsKey("pendente"), "Deve conter contagem de pendentes");
        assertEquals(2L, stats.get("pendente"), "Deve ter 2 emails pendentes");
        assertTrue(stats.containsKey("cancelado"), "Deve conter contagem de cancelados");
        assertEquals(1L, stats.get("cancelado"), "Deve ter 1 email cancelado");
    }

    private EmailCreateDTO criarEmailDto() {
        EmailCreateDTO dto = new EmailCreateDTO();
        dto.setDestinatario("dest@example.com");
        dto.setAssunto("Assunto teste");
        dto.setCorpo("Corpo do email de teste");
        return dto;
    }
}
