package br.com.erpkit.shared.exception;

/**
 * Marker para excecoes {@link ModuloException} que carregam um codigo de erro estruturado
 * (ex: "JANELA_24H_FECHADA", "META_ERROR") e opcionalmente um metaErrorCode numerico
 * (codigo numerico do provedor — ex: Meta error 131026 invalid phone).
 *
 * <p><b>Por que em lib-shared, nao em api-whatsapp:</b> permite que
 * {@link GlobalExceptionHandler} propague codigo+metaErrorCode SEM importar
 * pacotes de api-whatsapp (preservando direcao de dependencia: lib-shared NAO
 * pode depender de api-*). Phase 4 (api-whatsapp) cria
 * JanelaConversaFechadaException + MetaApiException, ambas extends ModuloException
 * implements CodigoCarrier.
 */
public interface CodigoCarrier {

    /**
     * Codigo de erro estruturado (snake_case maiusculo). Ex: {@code "JANELA_24H_FECHADA"},
     * {@code "META_ERROR"}, {@code "META_INDISPONIVEL"}, {@code "META_TIMEOUT"},
     * {@code "CIRCUIT_OPEN"}.
     *
     * @return codigo nao-nulo identificando a categoria do erro
     */
    String getCodigo();

    /**
     * Codigo numerico do provedor externo (ex: Meta API). Default null pois nem todo
     * codigo carrier precisa propagar metadado do provedor (ex: JANELA_24H_FECHADA e
     * decisao local, sem codigo Meta associado).
     *
     * @return codigo numerico do provedor ou {@code null} quando nao aplicavel
     */
    default Integer getMetaErrorCode() {
        return null;
    }
}
