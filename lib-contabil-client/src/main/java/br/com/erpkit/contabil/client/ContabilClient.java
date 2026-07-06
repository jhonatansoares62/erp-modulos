package br.com.erpkit.contabil.client;

import br.com.erpkit.contabil.client.dto.EventoContabilRequest;
import br.com.erpkit.contabil.client.dto.EventoRecebidoResponse;

/**
 * Cliente do módulo api-contabil. O ERP consumidor envia eventos de negócio
 * padronizados (venda, recebimento, compra, pagamento) e o módulo os traduz em
 * lançamentos contábeis via roteiros configuráveis. Ver CONTRATO-EVENTOS.md.
 */
public interface ContabilClient {

    /** Envia um evento de negócio para contabilização. Idempotente por eventoId. */
    EventoRecebidoResponse enviarEvento(EventoContabilRequest evento);

    boolean isOnline();

    String getCircuitBreakerState();

    boolean isHabilitado();
}
