package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Trava arquitetural #2 (OUT-06 + OUT-07 + D-03 + D-05 do PROJECT.md): consulta
 * {@code ultima_mensagem_em} de {@code whatsapp.clientes_zap} via native query
 * (skip JPA L1 cache, committed read fresco — PITFALLS C-01) e lanca
 * {@link JanelaConversaFechadaException} se diff &gt; 24h ou cliente inexistente.
 *
 * <p><b>Sem {@code @Transactional}</b>: leitura pura, query nativa contorna
 * cache. Phase 2 PER-07 escreve via REQUIRES_NEW + {@code NOW()} do banco; Phase
 * 4 le aqui de outra transacao (potencialmente sem transacao se chamado de
 * aspect outside Resilience4j chain — caso real do {@link
 * br.com.erpkit.whatsapp.aspect.JanelaEnforcementAspect}).
 *
 * <p><b>Normalizacao (PITFALLS C-13):</b> chama {@link TelefoneBR#normalizar}
 * antes da query — telefones SP/RJ/ES mantem 9o digito, demais strip — alinha
 * com o que Phase 2 PER-05 escreveu em {@code clientes_zap}. Sem isso, um
 * cliente registrado com 12 digitos seria visto como "nao registrado" se o ERP
 * pedisse envio com 13 digitos (falso negativo silencioso).
 *
 * <p><b>Por que duas excecoes do mesmo tipo:</b>
 * <ul>
 *   <li>{@code Optional.empty()} (cliente nao em {@code clientes_zap})
 *       lanca {@code JanelaConversaFechadaException} com {@code ultimaMensagemEm = null}.</li>
 *   <li>{@code diff > 24h} lanca com {@code ultimaMensagemEm} preenchido
 *       (Instant da ultima entrada).</li>
 * </ul>
 * Ambos sao "janela fechada" do ponto de vista do contrato HTTP (409 +
 * {@code JANELA_24H_FECHADA}); o {@code Instant} apenas enriquece o payload
 * para o ERP escalar com o cliente.
 *
 * <p>Diff calculado via {@code Duration.between(ultima, Instant.now())} usa o
 * relogio JVM apenas para calcular delta em horas (a referencia temporal
 * canonica esta no banco — Phase 2 PER-07 escreveu via {@code NOW()}), de
 * modo que clock skew JVM-DB de poucos segundos nao consegue mover um valor
 * de 23h59m para 24h01m em condicoes normais.
 */
@Service
public class WindowEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(WindowEnforcementService.class);
    private static final Duration JANELA = Duration.ofHours(24);

    private final ClienteZapRepository repository;

    public WindowEnforcementService(ClienteZapRepository repository) {
        this.repository = repository;
    }

    /**
     * Verifica que a janela 24h esta aberta para {@code telefone}. Caller deve
     * passar telefone em qualquer formato — esta service normaliza antes da
     * query.
     *
     * @param telefone numero em qualquer formato (com/sem +, paren, hifen, espacos)
     * @throws JanelaConversaFechadaException se cliente nao existe em
     *         {@code clientes_zap} (sem mensagem entrante registrada) ou se
     *         diff entre {@code ultima_mensagem_em} e {@code Instant.now()} &gt; 24h
     */
    public void verificarJanela(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        Optional<Instant> ultima = repository.buscarUltimaMensagemEm(normalizado);

        if (ultima.isEmpty()) {
            log.warn("Janela 24h: telefone={} sem mensagem entrante registrada", normalizado);
            throw new JanelaConversaFechadaException(normalizado, null);
        }

        Duration diff = Duration.between(ultima.get(), Instant.now());
        if (diff.compareTo(JANELA) > 0) {
            log.warn("Janela 24h fechada: telefone={} ultima_mensagem_em={} diff_horas={}",
                normalizado, ultima.get(), diff.toHours());
            throw new JanelaConversaFechadaException(normalizado, ultima.get());
        }

        log.debug("Janela 24h aberta: telefone={} diff_minutos={}",
            normalizado, diff.toMinutes());
    }
}
