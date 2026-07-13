package br.com.erpkit.whatsapp.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttle do aviso "fora do horario": evita spammar o paciente quando ele manda varias
 * mensagens fora do expediente. Manda o aviso no maximo 1x por telefone dentro da {@link #JANELA}.
 *
 * <p>Estado em memoria (o modulo e instancia unica por ERP, on-premise). Restart zera o mapa —
 * no pior caso um paciente recebe um aviso extra apos reboot, o que e aceitavel.
 */
@Component
public class AvisoForaHorarioThrottle {

    /** Janela de silencio: nao repete o aviso ao mesmo numero dentro dela. */
    private static final Duration JANELA = Duration.ofHours(6);

    private final Map<String, Instant> ultimoAviso = new ConcurrentHashMap<>();

    /**
     * True se deve enviar o aviso agora (nao avisou este numero na ultima {@link #JANELA}).
     * Registra o envio atomicamente quando retorna true.
     */
    public boolean deveAvisar(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return true;
        }
        Instant agora = Instant.now();
        boolean[] avisar = {false};
        ultimoAviso.compute(telefone, (k, anterior) -> {
            if (anterior != null && anterior.isAfter(agora.minus(JANELA))) {
                return anterior; // dentro da janela — mantem, nao avisa de novo
            }
            avisar[0] = true;
            return agora;
        });
        return avisar[0];
    }
}
