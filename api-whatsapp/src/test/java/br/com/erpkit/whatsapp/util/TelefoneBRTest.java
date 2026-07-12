package br.com.erpkit.whatsapp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelefoneBRTest {

    // ============================================================
    // Celular BR sem o 9 (12 digitos) → GARANTE o 9 — pivot do bug 131026
    // ============================================================

    @Test
    @DisplayName("DDD 46 (PR) sem o 9 recebe o 9 (55 + DDD + 8 -> 55 + DDD + 9 + 8)")
    void ddd46_sem_9_recebe_9() {
        assertThat(TelefoneBR.normalizar("554620009012")).isEqualTo("5546920009012");
    }

    @Test
    @DisplayName("DDD 47 (SC) sem o 9 (12 digitos) recebe o 9")
    void sc_ddd47_sem_9_recebe_9() {
        assertThat(TelefoneBR.normalizar("+554784178525")).isEqualTo("5547984178525");
    }

    @Test
    @DisplayName("Formato humano sem o 9 (parenteses/hifen) recebe o 9")
    void formatado_sem_9_recebe_9() {
        assertThat(TelefoneBR.normalizar("+55 (46) 2000-9012")).isEqualTo("5546920009012");
    }

    // ============================================================
    // Celular BR com o 9 (13 digitos) — canonico, passa direto (qualquer DDD)
    // ============================================================

    @Test
    @DisplayName("DDD 47 (SC) com o 9 preserva")
    void sc_ddd47_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5547984178525")).isEqualTo("5547984178525");
    }

    @Test
    @DisplayName("DDD 31 (MG) com o 9 preserva")
    void mg_ddd31_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5531987654321")).isEqualTo("5531987654321");
    }

    @Test
    @DisplayName("DDD 51 (RS) com o 9 preserva")
    void rs_ddd51_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5551987654321")).isEqualTo("5551987654321");
    }

    @Test
    @DisplayName("DDD 41 (PR) com o 9 preserva")
    void pr_ddd41_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5541987654321")).isEqualTo("5541987654321");
    }

    @Test
    @DisplayName("DDD 11 (SP) com o 9 preserva")
    void sp_ddd11_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5511987654321")).isEqualTo("5511987654321");
    }

    @Test
    @DisplayName("DDD 21 (RJ) com o 9 preserva")
    void rj_ddd21_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5521987654321")).isEqualTo("5521987654321");
    }

    @Test
    @DisplayName("DDD 19 (SP interior / TIM) com o 9 preserva")
    void sp_ddd19_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5519982583529")).isEqualTo("5519982583529");
    }

    // ============================================================
    // sanitizar() — wa_id EXATO do Meta (so digitos, NAO garante o 9)
    // ============================================================

    @Test
    @DisplayName("sanitizar remove nao-digitos e preserva exatamente o que veio")
    void sanitizar_preserva() {
        assertThat(TelefoneBR.sanitizar("+55 (47) 98417-8525")).isEqualTo("5547984178525");
    }

    @Test
    @DisplayName("sanitizar NAO adiciona o 9 (celular sem o 9 fica com 12 digitos)")
    void sanitizar_nao_adiciona_9() {
        assertThat(TelefoneBR.sanitizar("+554620009012")).isEqualTo("554620009012");
    }

    @Test
    @DisplayName("sanitizar(null) retorna null")
    void sanitizar_null() {
        assertThat(TelefoneBR.sanitizar(null)).isNull();
    }

    @Test
    @DisplayName("mesmo input sem o 9: sanitizar mantem 12 digitos, normalizar canonicaliza p/ 13 (com 9)")
    void sanitizar_vs_normalizar() {
        String semNove = "554620009012";  // celular BR sem o 9 (12 digitos)
        assertThat(TelefoneBR.sanitizar(semNove)).isEqualTo("554620009012");
        assertThat(TelefoneBR.normalizar(semNove)).isEqualTo("5546920009012");
    }

    // ============================================================
    // Casos edge (null, vazio, nao-Brasil, comprimentos estranhos)
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
    @DisplayName("Numero USA (+1) — sanitiza, nao toca (nao e BR)")
    void usa_nao_brasil() {
        assertThat(TelefoneBR.normalizar("+1 (415) 555-1212")).isEqualTo("14155551212");
    }

    @Test
    @DisplayName("Numero curto (10 digitos) — preserve sanitizado, nao tenta normalizar")
    void numero_curto() {
        assertThat(TelefoneBR.normalizar("4784178525")).isEqualTo("4784178525");
    }

    @Test
    @DisplayName("Numero estranho (15 digitos) — passa sem alteracao alem de sanitizar")
    void numero_muito_longo() {
        // 15 digitos foge do 12 esperado (12 = sem o 9) → preserve sanitizado
        assertThat(TelefoneBR.normalizar("+555111987654321")).isEqualTo("555111987654321");
    }
}
