package br.com.erpkit.consultas.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoValidatorTest {

    @Test
    void normalizaCepRemovendoMascara() {
        assertThat(DocumentoValidator.normalizarCep("01310-100")).isEqualTo("01310100");
        assertThat(DocumentoValidator.normalizarCep(" 01.310-100 ")).isEqualTo("01310100");
        assertThat(DocumentoValidator.normalizarCep(null)).isEmpty();
    }

    @Test
    void cepValidoSomente8Digitos() {
        assertThat(DocumentoValidator.cepValido("01310100")).isTrue();
        assertThat(DocumentoValidator.cepValido("0131010")).isFalse();
        assertThat(DocumentoValidator.cepValido("abc")).isFalse();
        assertThat(DocumentoValidator.cepValido(null)).isFalse();
    }

    @Test
    void cnpjValidoChecaDigitoVerificador() {
        // CNPJ Banco do Brasil - válido
        assertThat(DocumentoValidator.cnpjValido("00000000000191")).isTrue();
        // 14 dígitos iguais - inválido
        assertThat(DocumentoValidator.cnpjValido("11111111111111")).isFalse();
        // DV errado
        assertThat(DocumentoValidator.cnpjValido("00000000000192")).isFalse();
        // Tamanho errado
        assertThat(DocumentoValidator.cnpjValido("123")).isFalse();
        assertThat(DocumentoValidator.cnpjValido(null)).isFalse();
    }

    @Test
    void normalizaCnpjRemovendoPontuacao() {
        assertThat(DocumentoValidator.normalizarCnpj("00.000.000/0001-91")).isEqualTo("00000000000191");
    }
}
