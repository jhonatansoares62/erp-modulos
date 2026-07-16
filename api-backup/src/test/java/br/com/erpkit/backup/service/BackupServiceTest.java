package br.com.erpkit.backup.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logica pura do backup (sem Spring/DB): catch-up no boot e retencao GFS.
 * Portado do backup in-JVM do ERP.
 */
class BackupServiceTest {

    @Test
    void catchUpQuandoNuncaHouveBackup() {
        assertTrue(BackupService.precisaCatchUp(null, LocalDateTime.now()));
    }

    @Test
    void catchUpConformeIdadeDoUltimo() {
        LocalDateTime agora = LocalDateTime.of(2026, 7, 15, 12, 0);
        assertTrue(BackupService.precisaCatchUp(agora.minusHours(27), agora), ">26h dispara");
        assertFalse(BackupService.precisaCatchUp(agora.minusHours(1), agora), "recente nao dispara");
        assertFalse(BackupService.precisaCatchUp(agora.minusHours(26), agora), "26h exato nao dispara");
    }

    @Test
    void gfsMantemDiariosRecentesEDescartaAntigo() {
        LocalDate hoje = LocalDate.of(2026, 7, 15);
        List<LocalDate> diasDesc = new ArrayList<>(List.of(
                hoje, hoje.minusDays(1), hoje.minusDays(2), hoje.minusDays(400)));

        Set<LocalDate> manter = BackupService.diasParaManter(diasDesc, 2, 1, 1);

        assertTrue(manter.contains(hoje));
        assertTrue(manter.contains(hoje.minusDays(1)));
        assertFalse(manter.contains(hoje.minusDays(2)), "fora do daily(2) e nao e o topo da semana/mes");
        assertFalse(manter.contains(hoje.minusDays(400)), "mes antigo, fora do monthly(1)");
    }

    @Test
    void gfsMantemTudoQuandoDailyCobre() {
        LocalDate hoje = LocalDate.of(2026, 7, 15);
        List<LocalDate> diasDesc = new ArrayList<>(List.of(hoje, hoje.minusDays(1), hoje.minusDays(2)));

        Set<LocalDate> manter = BackupService.diasParaManter(diasDesc, 10, 4, 6);

        assertEquals(3, manter.size());
    }
}
