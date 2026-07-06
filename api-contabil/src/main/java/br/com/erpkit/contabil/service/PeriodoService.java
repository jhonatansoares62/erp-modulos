package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.model.PeriodoFechado;
import br.com.erpkit.contabil.repository.PeriodoFechadoRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fechamento de período (lock date). Um lançamento cujo mês de competência é anterior ou igual
 * ao último período mensal fechado é rejeitado (F8). Fechar não zera nada (só trava); o
 * encerramento de exercício (que zera resultado) é wave posterior.
 */
@Service
public class PeriodoService {

    private static final Pattern MENSAL = Pattern.compile("\\d{4}-\\d{2}");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PeriodoFechadoRepository repository;

    public PeriodoService(PeriodoFechadoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PeriodoFechado fecharMensal(String competencia, String fechadoPor) {
        if (competencia == null || !MENSAL.matcher(competencia).matches()) {
            throw new ModuloException("Competência mensal inválida (use YYYY-MM): " + competencia);
        }
        if (repository.existsByCompetenciaAndTipo(competencia, "mensal")) {
            throw new ModuloException("Período " + competencia + " já está fechado", HttpStatus.CONFLICT);
        }
        PeriodoFechado p = new PeriodoFechado();
        p.setCompetencia(competencia);
        p.setTipo("mensal");
        p.setFechadoPor(fechadoPor);
        return repository.save(p);
    }

    public List<PeriodoFechado> listar() {
        return repository.findAllByOrderByCompetenciaDesc();
    }

    /** Rejeita lançamento em período fechado: mês da competência <= último mês fechado. */
    public void validarPeriodoAberto(LocalDate dataCompetencia) {
        repository.findFirstByTipoOrderByCompetenciaDesc("mensal").ifPresent(ultimo -> {
            String mesLanc = dataCompetencia.format(YM);
            if (mesLanc.compareTo(ultimo.getCompetencia()) <= 0) {
                throw new ModuloException("Período " + ultimo.getCompetencia()
                        + " está fechado; não é possível lançar em " + mesLanc);
            }
        });
    }
}
