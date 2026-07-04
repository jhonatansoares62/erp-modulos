package br.com.erpkit.whatsapp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelefoneBRTest {

    // ============================================================
    // Casos OUTROS DDDs (strip 9o digito) — pivot do bug 131026
    // ============================================================

    @Test
    @DisplayName("DDD 47 (SC) com 9o digito strip o 9")
    void sc_dd47_com_9() {
        assertThat(TelefoneBR.normalizar("+5547984178525")).isEqualTo("554784178525");
    }

    @Test
    @DisplayName("DDD 31 (MG) com 9o digito strip o 9")
    void mg_ddd31_com_9() {
        assertThat(TelefoneBR.normalizar("+5531987654321")).isEqualTo("553187654321");
    }

    @Test
    @DisplayName("DDD 51 (RS) com 9o digito strip o 9")
    void rs_ddd51_com_9() {
        assertThat(TelefoneBR.normalizar("+5551987654321")).isEqualTo("555187654321");
    }

    @Test
    @DisplayName("DDD 41 (PR) com 9o digito strip o 9")
    void pr_ddd41_com_9() {
        assertThat(TelefoneBR.normalizar("+5541987654321")).isEqualTo("554187654321");
    }

    // ============================================================
    // sanitizar() — wa_id EXATO do Meta (NAO strip o 9) p/ resposta outbound
    // ============================================================

    @Test
    @DisplayName("sanitizar preserva o 9o digito (wa_id do Meta) e remove nao-digitos")
    void sanitizar_preserva_9() {
        assertThat(TelefoneBR.sanitizar("+55 (47) 98417-8525")).isEqualTo("5547984178525");
    }

    @Test
    @DisplayName("sanitizar(null) retorna null")
    void sanitizar_null() {
        assertThat(TelefoneBR.sanitizar(null)).isNull();
    }

    @Test
    @DisplayName("mesmo input: sanitizar mantem o 9 (resposta), normalizar strip o 9 (interno)")
    void sanitizar_vs_normalizar() {
        String waId = "5547984178525";  // wa_id do Meta (DDD 47, com 9)
        assertThat(TelefoneBR.sanitizar(waId)).isEqualTo("5547984178525");
        assertThat(TelefoneBR.normalizar(waId)).isEqualTo("554784178525");
    }

    // ============================================================
    // Casos SP/RJ/ES (mantem 9o digito)
    // ============================================================

    @Test
    @DisplayName("DDD 11 (SP) mantem 9o digito")
    void sp_ddd11_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5511987654321")).isEqualTo("5511987654321");
    }

    @Test
    @DisplayName("DDD 21 (RJ) mantem 9o digito")
    void rj_ddd21_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5521987654321")).isEqualTo("5521987654321");
    }

    @Test
    @DisplayName("DDD 24 (RJ interior) mantem 9o digito")
    void rj_ddd24_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5524987654321")).isEqualTo("5524987654321");
    }

    @Test
    @DisplayName("DDD 27 (ES) mantem 9o digito")
    void es_ddd27_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5527987654321")).isEqualTo("5527987654321");
    }

    @Test
    @DisplayName("DDD 19 (SP interior) mantem 9o digito")
    void sp_ddd19_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5519987654321")).isEqualTo("5519987654321");
    }

    // ============================================================
    // Casos formato/sanitizacao
    // ============================================================

    @Test
    @DisplayName("Formato com parenteses e hifen e espaco — sanitiza e normaliza")
    void formatado_humano() {
        assertThat(TelefoneBR.normalizar("+55 (47) 98417-8525")).isEqualTo("554784178525");
    }

    @Test
    @DisplayName("Formato com so digitos sem prefixo + — normaliza igual")
    void sem_plus() {
        assertThat(TelefoneBR.normalizar("5547984178525")).isEqualTo("554784178525");
    }

    @Test
    @DisplayName("Numero ja sem 9o digito (8 digitos locais) passa direto")
    void ja_sem_nono() {
        // 12 digitos: 55 + 47 + 8 digitos = ja em formato Meta-friendly
        assertThat(TelefoneBR.normalizar("+554784178525")).isEqualTo("554784178525");
    }

    // ============================================================
    // Casos edge (null, vazio, nao-Brasil)
    // ============================================================

    @Test
    @DisplayName("null retorna null")
    void null_retorna_null() {
        assertThat(TelefoneBR.normalizar(null)).isNull();
    }

    @Test
    @DisplayName("string vazia retorna string vazia")
    void empty_retorna_empty() {
        assertThat(TelefoneBR.normalizar("")).isEmpty();
    }

    @Test
    @DisplayName("string sem digitos retorna string vazia")
    void sem_digitos_retorna_empty() {
        assertThat(TelefoneBR.normalizar("()-+ ")).isEmpty();
    }

    @Test
    @DisplayName("Numero USA (+1) — sanitiza, nao toca prefix nem strip")
    void usa_nao_brasil() {
        assertThat(TelefoneBR.normalizar("+1 (415) 555-1212")).isEqualTo("14155551212");
    }

    @Test
    @DisplayName("Numero curto (10 digitos) — preserve sanitizado, nao tenta normalizar")
    void numero_curto() {
        assertThat(TelefoneBR.normalizar("4784178525")).isEqualTo("4784178525");
    }

    // ============================================================
    // Casos exotic (DDD valido estruturalmente mas inexistente)
    // ============================================================

    @Test
    @DisplayName("DDD inexistente (99) com 9o digito — strip aplica baseado no Set, nao em validacao real de DDD")
    void ddd_inexistente_99_strip_9() {
        // Politica: a logica e baseada no Set lookup, nao em validacao do DDD existir.
        // DDD 99 nao esta no Set DDDS_COM_NONO_DIGITO → strip aplica.
        assertThat(TelefoneBR.normalizar("+5599987654321")).isEqualTo("559987654321");
    }

    @Test
    @DisplayName("Numero estranho (14 digitos) — passa sem alteracao alem de sanitizar")
    void numero_muito_longo() {
        // 14 digitos foge do {12,13} esperado → preserve sanitizado
        assertThat(TelefoneBR.normalizar("+555111987654321")).isEqualTo("555111987654321");
    }
}
