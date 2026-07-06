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

    /** Proxy genérico GET para a API do módulo (ex.: "/v1/contas/arvore"). Retorna o JSON cru. */
    String proxyGet(String path);

    /** Proxy genérico POST para a API do módulo (ex.: "/v1/pendencias/{id}/salvar-regra"). */
    String proxyPost(String path, String body);

    /** Proxy genérico PUT para a API do módulo (ex.: "/v1/contas/{id}"). */
    String proxyPut(String path, String body);

    /** Proxy genérico DELETE para a API do módulo (ex.: "/v1/contas/{id}"). */
    String proxyDelete(String path);

    boolean isOnline();

    String getCircuitBreakerState();

    boolean isHabilitado();
}
