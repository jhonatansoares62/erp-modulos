package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Desfecho que o ERP devolve no callback {@code POST /api/modulos/whatsapp/comando}
 * (§12 #6): o que aconteceu com o comando do paciente. Antes o corpo da resposta do
 * ERP era descartado; agora {@code ErpCallbackClient} o le e o modulo persiste em
 * {@code mensagens_log.resultado} para medir efetividade do bot.
 *
 * @param resultado respondido | nao_entendi | sem_resposta | erro
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DesfechoCallbackDTO(String resultado) { }
