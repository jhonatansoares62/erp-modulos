package br.com.erpkit.whatsapp.exception;

import br.com.erpkit.shared.exception.CodigoCarrier;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Janela de 24h da conversa esta fechada — ERP tentou enviar mensagem para cliente
 * cuja ultima mensagem entrante foi ha mais de 24h, ou cliente sem nenhuma mensagem
 * entrante registrada.
 *
 * <p><b>HTTP 409 + codigo {@code JANELA_24H_FECHADA}</b> (D-02 do CONTEXT.md).
 * Nao retentavel — ERP precisa esperar nova mensagem entrante do cliente para
 * reabrir a janela. Implementa {@link CodigoCarrier} para propagacao automatica
 * via {@code GlobalExceptionHandler} (lib-shared Phase 4 04-01) que faz
 * {@code instanceof CodigoCarrier} sem precisar importar pacotes de api-*.
 *
 * <p><b>Trava custo zero #2</b> (PROJECT.md D5 + REQUIREMENTS OUT-06): garante
 * por arquitetura — antes de qualquer byte ir para Meta — que mensagens
 * nao-template fora da janela 24h sao rejeitadas. Sem isso, Meta poderia
 * silenciosamente processar como session conversation paga (PITFALLS C-01).
 *
 * <p>Carrega {@code telefone} normalizado e o {@link Instant} da ultima mensagem
 * entrante (ou {@code null} se o cliente nao tinha registro algum em
 * {@code clientes_zap}) para que o handler global construa um {@code ErrorResponse}
 * informativo e o ERP consiga decidir como escalar com o cliente.
 */
public class JanelaConversaFechadaException extends ModuloException implements CodigoCarrier {

    /** Codigo estruturado do erro — propagado via {@link CodigoCarrier#getCodigo()}. */
    public static final String CODIGO = "JANELA_24H_FECHADA";

    private final String telefone;
    private final Instant ultimaMensagemEm;

    /**
     * @param telefone telefone JA NORMALIZADO via {@code TelefoneBR.normalizar}
     *                 (caller — {@code WindowEnforcementService} — responsavel)
     * @param ultimaMensagemEm Instant da ultima mensagem entrante registrada;
     *                         {@code null} quando o cliente nao existe em
     *                         {@code clientes_zap} (nunca recebeu mensagem do
     *                         lado dele) — caso semanticamente diferente de
     *                         "existe mas a janela passou".
     */
    public JanelaConversaFechadaException(String telefone, Instant ultimaMensagemEm) {
        super(montarMensagem(telefone, ultimaMensagemEm), HttpStatus.CONFLICT);
        this.telefone = telefone;
        this.ultimaMensagemEm = ultimaMensagemEm;
    }

    @Override
    public String getCodigo() {
        return CODIGO;
    }

    public String getTelefone() {
        return telefone;
    }

    public Instant getUltimaMensagemEm() {
        return ultimaMensagemEm;
    }

    private static String montarMensagem(String telefone, Instant ultima) {
        if (ultima == null) {
            return "Janela 24h: telefone " + telefone + " sem mensagem entrante registrada";
        }
        return "Janela 24h fechada: telefone " + telefone + " ultima entrante em " + ultima;
    }
}
