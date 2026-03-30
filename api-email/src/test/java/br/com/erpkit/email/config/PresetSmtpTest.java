package br.com.erpkit.email.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetSmtpTest {

    @Test
    @DisplayName("Deve buscar preset Gmail com dados corretos")
    void deveBuscarPresetGmail() {
        PresetSmtp preset = PresetSmtp.buscar("gmail");

        assertNotNull(preset, "Preset Gmail nao deve ser nulo");
        assertEquals("smtp.gmail.com", preset.getHost(), "Host deve ser smtp.gmail.com");
        assertEquals(587, preset.getPorta(), "Porta deve ser 587");
        assertTrue(preset.isTls(), "TLS deve estar habilitado");
        assertNotNull(preset.getInstrucoes(), "Instrucoes nao devem ser nulas");
    }

    @Test
    @DisplayName("Deve buscar preset Outlook com dados corretos")
    void deveBuscarPresetOutlook() {
        PresetSmtp preset = PresetSmtp.buscar("outlook");

        assertNotNull(preset, "Preset Outlook nao deve ser nulo");
        assertEquals("smtp.office365.com", preset.getHost(), "Host deve ser smtp.office365.com");
        assertEquals(587, preset.getPorta(), "Porta deve ser 587");
        assertTrue(preset.isTls(), "TLS deve estar habilitado");
    }

    @Test
    @DisplayName("Deve buscar preset Mailtrap para teste")
    void deveBuscarPresetMailtrap() {
        PresetSmtp preset = PresetSmtp.buscar("mailtrap");

        assertNotNull(preset, "Preset Mailtrap nao deve ser nulo");
        assertEquals("sandbox.smtp.mailtrap.io", preset.getHost(), "Host deve ser sandbox.smtp.mailtrap.io");
    }

    @Test
    @DisplayName("Deve retornar null para preset desconhecido")
    void deveRetornarNullParaPresetDesconhecido() {
        PresetSmtp preset = PresetSmtp.buscar("provedor-inexistente");

        assertNull(preset, "Deve retornar null para preset desconhecido");
    }

    @Test
    @DisplayName("Deve buscar preset ignorando maiusculas")
    void deveBuscarPresetIgnorandoMaiusculas() {
        PresetSmtp preset = PresetSmtp.buscar("GMAIL");

        assertNotNull(preset, "Deve encontrar preset mesmo em maiusculas");
        assertEquals("smtp.gmail.com", preset.getHost(), "Host deve ser smtp.gmail.com");
    }

    @Test
    @DisplayName("Deve buscar preset com espacos extras")
    void deveBuscarPresetComEspacosExtras() {
        PresetSmtp preset = PresetSmtp.buscar("  gmail  ");

        assertNotNull(preset, "Deve encontrar preset mesmo com espacos extras");
        assertEquals("smtp.gmail.com", preset.getHost(), "Host deve ser smtp.gmail.com");
    }

    @Test
    @DisplayName("Deve retornar todos os presets")
    void deveRetornarTodosOsPresets() {
        Map<String, PresetSmtp> todos = PresetSmtp.todos();

        assertNotNull(todos, "Mapa de presets nao deve ser nulo");
        assertFalse(todos.isEmpty(), "Mapa de presets nao deve ser vazio");
        assertTrue(todos.containsKey("gmail"), "Deve conter preset gmail");
        assertTrue(todos.containsKey("outlook"), "Deve conter preset outlook");
        assertTrue(todos.containsKey("sendgrid"), "Deve conter preset sendgrid");
        assertTrue(todos.containsKey("mailtrap"), "Deve conter preset mailtrap");
        assertTrue(todos.size() >= 10, "Deve ter pelo menos 10 presets configurados");
    }

    @Test
    @DisplayName("Deve ter presets brasileiros configurados")
    void deveTerPresetsBrasileiros() {
        Map<String, PresetSmtp> todos = PresetSmtp.todos();

        assertTrue(todos.containsKey("uol"), "Deve conter preset UOL");
        assertTrue(todos.containsKey("bol"), "Deve conter preset BOL");
        assertTrue(todos.containsKey("locaweb"), "Deve conter preset Locaweb");
    }
}
