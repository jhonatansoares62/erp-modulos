package br.com.erpkit.whatsapp.client;

import br.com.erpkit.whatsapp.client.dto.BotaoDto;
import br.com.erpkit.whatsapp.client.dto.EnvioResponse;
import br.com.erpkit.whatsapp.client.dto.MetaConfigRequest;
import br.com.erpkit.whatsapp.client.dto.MetaConfigResponse;
import br.com.erpkit.whatsapp.client.dto.ResumoUsoResponse;
import br.com.erpkit.whatsapp.client.dto.SecaoDto;
import br.com.erpkit.whatsapp.client.dto.StatusResponse;
import br.com.erpkit.whatsapp.client.dto.WhatsAppRespostaDto;

import java.util.List;

/**
 * Cliente HTTP do api-whatsapp para o ERP enviar mensagens outbound e consultar
 * status. Espelha o mental model de {@code ConsultasClient} (mesma injecao, mesma
 * config Resilience4j).
 *
 * <p>Dois layers de API:
 * <ul>
 *   <li><b>Layer 1 (tipado):</b> {@code enviarTexto/Documento/Botoes/Lista} — o ERP
 *       que sabe o tipo chama direto.</li>
 *   <li><b>Layer 2 (conveniencia):</b> {@link #despachar(String, WhatsAppRespostaDto)}
 *       — despacha por {@code Tipo}; usado pelo {@code WhatsAppCommandRegistry}.</li>
 * </ul>
 *
 * <p><b>Custo zero de Meta por design:</b> nao ha metodo de template. So e possivel
 * responder dentro da janela 24h — a trava vive no api-whatsapp.
 */
public interface WhatsAppClient {

    EnvioResponse enviarTexto(String telefone, String texto);

    EnvioResponse enviarDocumento(String telefone, byte[] bytes, String filename, String mimeType, String caption);

    /** Maximo 3 botoes (limite Meta). */
    EnvioResponse enviarBotoes(String telefone, String texto, List<BotaoDto> botoes);

    /** Maximo 10 itens somando todas as secoes (limite Meta). */
    EnvioResponse enviarLista(String telefone, String texto, List<SecaoDto> secoes);

    /**
     * Despacha uma {@link WhatsAppRespostaDto} para o metodo tipado correto conforme
     * {@code resposta.tipo()}. {@code null} retorna {@code null} (handler nao respondeu).
     */
    EnvioResponse despachar(String telefone, WhatsAppRespostaDto resposta);

    /** Proxy do {@code GET /api/whatsapp/status}. */
    StatusResponse status();

    /** Proxy do {@code GET /api/whatsapp/config} — credenciais Meta com secrets mascarados. */
    MetaConfigResponse obterConfig();

    /** Proxy do {@code PUT /api/whatsapp/config} — grava as credenciais Meta (atualizacao parcial). */
    MetaConfigResponse salvarConfig(MetaConfigRequest req);

    /**
     * Proxy do {@code GET /api/whatsapp/relatorios/resumo} — resumo de uso do periodo
     * (V7 §12). {@code de}/{@code ate} sao ISO-8601 opcionais ({@code null} = ultimos 30 dias).
     */
    ResumoUsoResponse relatorioResumo(String de, String ate);

    /** Health check leve (nao lanca excecao; retorna {@code false} se offline/desabilitado). */
    boolean isOnline();

    /** Espelha {@code props.enabled}. */
    boolean isHabilitado();

    /** Estado do circuit breaker: CLOSED | OPEN | HALF_OPEN. */
    String getCircuitBreakerState();
}
