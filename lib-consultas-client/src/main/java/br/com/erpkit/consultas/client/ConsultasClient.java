package br.com.erpkit.consultas.client;

import br.com.erpkit.consultas.client.dto.EnderecoResponse;
import br.com.erpkit.consultas.client.dto.FornecedorResponse;

public interface ConsultasClient {

    EnderecoResponse consultarCep(String cep);

    FornecedorResponse consultarCnpj(String cnpj);

    boolean isOnline();

    String getCircuitBreakerState();

    boolean isHabilitado();
}
