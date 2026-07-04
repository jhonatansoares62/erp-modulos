package br.com.erpkit.whatsapp.client;

import br.com.erpkit.whatsapp.client.dto.WhatsAppComandoDto;
import br.com.erpkit.whatsapp.client.dto.WhatsAppRespostaDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppCommandRegistryTest {

    /** Handler de teste; opcionalmente casa por prefixo. */
    private static class TestHandler implements WhatsAppCommandHandler {
        private final String comando;
        private final boolean prefixo;

        TestHandler(String comando) {
            this(comando, false);
        }

        TestHandler(String comando, boolean prefixo) {
            this.comando = comando;
            this.prefixo = prefixo;
        }

        @Override
        public String getComando() {
            return comando;
        }

        @Override
        public boolean matches(String c) {
            if (prefixo) {
                return c != null && c.toLowerCase().startsWith(comando.toLowerCase());
            }
            return WhatsAppCommandHandler.super.matches(c);
        }

        @Override
        public WhatsAppRespostaDto processar(WhatsAppComandoDto cmd) {
            return WhatsAppRespostaDto.texto("ok:" + comando);
        }
    }

    @Test
    void exactMatchResolveHandlerCorreto() {
        var orcamento = new TestHandler("orcamento");
        var boleto = new TestHandler("boleto");
        var registry = new WhatsAppCommandRegistry(List.of(orcamento, boleto));

        assertThat(registry.resolver("orcamento")).containsSame(orcamento);
        assertThat(registry.resolver("boleto")).containsSame(boleto);
    }

    @Test
    void prefixFallbackCasaHandlerComOverride() {
        var aprovar = new TestHandler("aprovar", true);
        var registry = new WhatsAppCommandRegistry(List.of(aprovar));

        assertThat(registry.resolver("aprovar 1234")).containsSame(aprovar);
    }

    @Test
    void resolucaoEhCaseInsensitive() {
        var orcamento = new TestHandler("Orcamento");
        var registry = new WhatsAppCommandRegistry(List.of(orcamento));

        assertThat(registry.resolver("orcamento")).containsSame(orcamento);
        assertThat(registry.resolver("ORCAMENTO")).containsSame(orcamento);
    }

    @Test
    void comandoNuloOuVazioRetornaEmpty() {
        var registry = new WhatsAppCommandRegistry(List.of(new TestHandler("orcamento")));

        assertThat(registry.resolver(null)).isEmpty();
        assertThat(registry.resolver("")).isEmpty();
        assertThat(registry.resolver("   ")).isEmpty();
    }

    @Test
    void nenhumHandlerCasaRetornaEmpty() {
        var registry = new WhatsAppCommandRegistry(List.of(new TestHandler("orcamento")));

        assertThat(registry.resolver("inexistente")).isEmpty();
    }

    @Test
    void colisaoExactMatchPrimeiroRegistradoVence() {
        var primeiro = new TestHandler("orcamento");
        var segundo = new TestHandler("orcamento");
        var registry = new WhatsAppCommandRegistry(List.of(primeiro, segundo));

        assertThat(registry.resolver("orcamento")).containsSame(primeiro);
    }
}
