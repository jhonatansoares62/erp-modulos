package br.com.erpkit.email.service;

import br.com.erpkit.email.dto.ContaEmailCreateDTO;
import br.com.erpkit.email.dto.ContaEmailResponse;
import br.com.erpkit.email.model.ContaEmail;
import br.com.erpkit.email.repository.ContaEmailRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ContaEmailServiceTest {

    @Autowired
    private ContaEmailService contaEmailService;

    @Autowired
    private ContaEmailRepository contaEmailRepository;

    @BeforeEach
    void setUp() {
        contaEmailRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve criar conta com preset e resolver host/porta")
    void deveCriarContaComPresetResolvendoHostPorta() {
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setPreset("gmail");
        dto.setUsername("user@gmail.com");
        dto.setPassword("app-password");
        dto.setRemetente("user@gmail.com");

        ContaEmailResponse response = contaEmailService.criar(dto);

        assertNotNull(response.getId(), "ID nao deve ser nulo");
        assertEquals("gmail", response.getPreset(), "Preset deve ser gmail");
        assertEquals("smtp.gmail.com", response.getHost(), "Host deve ser resolvido do preset");
        assertEquals(587, response.getPorta(), "Porta deve ser resolvida do preset");
        assertTrue(response.isTls(), "TLS deve estar habilitado");
        assertTrue(response.isAtivo(), "Conta deve estar ativa");
    }

    @Test
    @DisplayName("Deve criar conta com host manual quando sem preset")
    void deveCriarContaComHostManual() {
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setHost("smtp.meudominio.com.br");
        dto.setPorta(465);
        dto.setUsername("contato@meudominio.com.br");
        dto.setPassword("senha123");
        dto.setRemetente("contato@meudominio.com.br");

        ContaEmailResponse response = contaEmailService.criar(dto);

        assertNotNull(response.getId(), "ID nao deve ser nulo");
        assertEquals("smtp.meudominio.com.br", response.getHost(), "Host deve ser o informado manualmente");
        assertEquals(465, response.getPorta(), "Porta deve ser a informada manualmente");
    }

    @Test
    @DisplayName("Deve lancar excecao ao criar sem preset e sem host")
    void deveLancarExcecaoSemPresetESemHost() {
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setUsername("user");
        dto.setPassword("pass");
        dto.setRemetente("user@test.com");

        ModuloException ex = assertThrows(ModuloException.class, () -> contaEmailService.criar(dto),
                "Deve lancar ModuloException sem preset e sem host");
        assertTrue(ex.getMessage().contains("preset") || ex.getMessage().contains("host"),
                "Mensagem deve mencionar preset ou host");
    }

    @Test
    @DisplayName("Deve lancar excecao ao usar preset invalido")
    void deveLancarExcecaoComPresetInvalido() {
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setPreset("provedor-fake");
        dto.setUsername("user");
        dto.setPassword("pass");
        dto.setRemetente("user@test.com");

        ModuloException ex = assertThrows(ModuloException.class, () -> contaEmailService.criar(dto),
                "Deve lancar ModuloException com preset invalido");
        assertTrue(ex.getMessage().contains("Preset"), "Mensagem deve mencionar Preset");
    }

    @Test
    @DisplayName("Deve marcar como padrao e remover padrao anterior")
    void deveMarcarComoPadraoERemovedPadraoAnterior() {
        // Criar primeira conta como padrao
        ContaEmailCreateDTO dto1 = new ContaEmailCreateDTO();
        dto1.setPreset("gmail");
        dto1.setUsername("user1@gmail.com");
        dto1.setPassword("pass1");
        dto1.setRemetente("user1@gmail.com");
        dto1.setPadrao(true);

        ContaEmailResponse resp1 = contaEmailService.criar(dto1);
        assertTrue(resp1.isPadrao(), "Primeira conta deve ser padrao");

        // Criar segunda conta como padrao
        ContaEmailCreateDTO dto2 = new ContaEmailCreateDTO();
        dto2.setPreset("outlook");
        dto2.setUsername("user2@outlook.com");
        dto2.setPassword("pass2");
        dto2.setRemetente("user2@outlook.com");
        dto2.setPadrao(true);

        ContaEmailResponse resp2 = contaEmailService.criar(dto2);
        assertTrue(resp2.isPadrao(), "Segunda conta deve ser padrao");

        // Verificar que a primeira nao e mais padrao
        ContaEmailResponse resp1Atualizada = contaEmailService.buscar(resp1.getId());
        assertFalse(resp1Atualizada.isPadrao(), "Primeira conta nao deve mais ser padrao");
    }

    @Test
    @DisplayName("Deve buscar conta padrao para envio quando contaId e null")
    void deveBuscarContaPadraoParaEnvioQuandoContaIdNull() {
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setPreset("gmail");
        dto.setUsername("user@gmail.com");
        dto.setPassword("pass");
        dto.setRemetente("user@gmail.com");
        dto.setPadrao(true);

        contaEmailService.criar(dto);

        ContaEmail conta = contaEmailService.buscarContaParaEnvio(null);

        assertNotNull(conta, "Deve encontrar conta padrao");
        assertEquals("user@gmail.com", conta.getUsername(), "Username deve corresponder");
    }

    @Test
    @DisplayName("Deve buscar conta especifica para envio com contaId valido")
    void deveBuscarContaEspecificaParaEnvioComContaIdValido() {
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setPreset("gmail");
        dto.setUsername("user@gmail.com");
        dto.setPassword("pass");
        dto.setRemetente("user@gmail.com");

        ContaEmailResponse response = contaEmailService.criar(dto);

        ContaEmail conta = contaEmailService.buscarContaParaEnvio(response.getId());

        assertNotNull(conta, "Deve encontrar conta pelo id");
        assertEquals(response.getId(), conta.getId(), "ID deve corresponder");
    }

    @Test
    @DisplayName("Deve lancar excecao ao buscar conta com ID inexistente para envio")
    void deveLancarExcecaoAoBuscarContaComIdInexistenteParaEnvio() {
        ModuloException ex = assertThrows(ModuloException.class,
                () -> contaEmailService.buscarContaParaEnvio(999L),
                "Deve lancar ModuloException com ID inexistente");
        assertTrue(ex.getMessage().contains("encontrad"), "Mensagem deve indicar que conta nao foi encontrada");
    }

    @Test
    @DisplayName("Deve lancar excecao quando nenhuma conta padrao configurada")
    void deveLancarExcecaoQuandoNenhumaContaPadraoConfigurada() {
        // Nao criar nenhuma conta padrao
        ModuloException ex = assertThrows(ModuloException.class,
                () -> contaEmailService.buscarContaParaEnvio(null),
                "Deve lancar ModuloException sem conta padrao");
        assertTrue(ex.getMessage().contains("padrao") || ex.getMessage().contains("padrão"),
                "Mensagem deve mencionar conta padrao");
    }

    @Test
    @DisplayName("Deve desativar conta e remover padrao")
    void deveDesativarContaERemoverPadrao() {
        ContaEmailCreateDTO dto = new ContaEmailCreateDTO();
        dto.setPreset("gmail");
        dto.setUsername("user@gmail.com");
        dto.setPassword("pass");
        dto.setRemetente("user@gmail.com");
        dto.setPadrao(true);

        ContaEmailResponse response = contaEmailService.criar(dto);

        contaEmailService.desativar(response.getId());

        ContaEmailResponse desativada = contaEmailService.buscar(response.getId());
        assertFalse(desativada.isAtivo(), "Conta deve estar desativada");
        assertFalse(desativada.isPadrao(), "Conta desativada nao deve ser padrao");
    }

    @Test
    @DisplayName("Deve listar presets disponiveis")
    void deveListarPresetsDisponiveis() {
        var presets = contaEmailService.listarPresets();

        assertNotNull(presets, "Lista de presets nao deve ser nula");
        assertFalse(presets.isEmpty(), "Lista de presets nao deve ser vazia");
        assertTrue(presets.stream().anyMatch(p -> "gmail".equals(p.getCodigo())),
                "Lista deve conter preset gmail");
    }
}
