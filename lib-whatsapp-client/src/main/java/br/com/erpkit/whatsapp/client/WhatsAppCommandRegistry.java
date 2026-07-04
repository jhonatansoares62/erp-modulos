package br.com.erpkit.whatsapp.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Roteia comandos entrantes para o {@link WhatsAppCommandHandler} correto (D-03).
 * Thread-safe por construcao: {@code exactMap} e {@code handlers} sao imutaveis apos
 * o construtor, entao {@link #resolver(String)} nao precisa de sincronizacao.
 *
 * <p>Roteamento em 2 tiers:
 * <ol>
 *   <li><b>Tier 1 (exact O(1)):</b> lookup em {@code Map} por {@code getComando().toLowerCase()}
 *       — cobre ~95% dos casos. Em colisao, o primeiro handler registrado vence.</li>
 *   <li><b>Tier 2 (fallback iter):</b> itera os handlers chamando {@link WhatsAppCommandHandler#matches(String)}
 *       na ordem de registro (Spring DI order / {@code @Order}); o primeiro {@code true} vence.</li>
 * </ol>
 */
public class WhatsAppCommandRegistry {

    private final List<WhatsAppCommandHandler> handlers;
    private final Map<String, WhatsAppCommandHandler> exactMap;

    public WhatsAppCommandRegistry(List<WhatsAppCommandHandler> handlers) {
        this.handlers = List.copyOf(handlers);
        Map<String, WhatsAppCommandHandler> map = new HashMap<>();
        for (WhatsAppCommandHandler h : this.handlers) {
            String comando = h.getComando();
            if (comando != null && !comando.isBlank()) {
                map.putIfAbsent(comando.toLowerCase(), h); // primeiro-registrado vence
            }
        }
        this.exactMap = Map.copyOf(map);
    }

    /**
     * Resolve o handler para um comando entrante. {@code Optional.empty()} se o comando
     * for nulo/vazio ou nenhum handler casar.
     */
    public Optional<WhatsAppCommandHandler> resolver(String comando) {
        if (comando == null || comando.isBlank()) {
            return Optional.empty();
        }
        String normalizado = comando.trim();

        // Tier 1: exact-match O(1)
        WhatsAppCommandHandler exato = exactMap.get(normalizado.toLowerCase());
        if (exato != null) {
            return Optional.of(exato);
        }

        // Tier 2: fallback iter — primeiro match vence
        return handlers.stream()
                .filter(h -> h.matches(normalizado))
                .findFirst();
    }

    /** Handlers registrados, na ordem de registro (imutavel). */
    public List<WhatsAppCommandHandler> getHandlers() {
        return handlers;
    }
}
