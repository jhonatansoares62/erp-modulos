package br.com.erpkit.consultas.service;

import br.com.erpkit.consultas.client.dto.FornecedorResponse;
import br.com.erpkit.consultas.service.provider.BrasilApiProvider;
import br.com.erpkit.consultas.service.provider.ProviderIndisponivelException;
import br.com.erpkit.consultas.validation.DocumentoValidator;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CnpjService {

    private final BrasilApiProvider brasilApi;

    public CnpjService(BrasilApiProvider brasilApi) {
        this.brasilApi = brasilApi;
    }

    @Cacheable(value = "cnpj", key = "#cnpjInput")
    public FornecedorResponse consultar(String cnpjInput) {
        String cnpj = DocumentoValidator.normalizarCnpj(cnpjInput);
        if (!DocumentoValidator.cnpjValido(cnpj)) {
            throw new ModuloException("CNPJ inválido (formato ou dígito verificador)");
        }

        try {
            return brasilApi.consultarCnpj(cnpj).orElseThrow(() ->
                    new ModuloException("CNPJ " + cnpj + " não encontrado", HttpStatus.NOT_FOUND));
        } catch (ProviderIndisponivelException e) {
            throw new ModuloException("Provedor de CNPJ indisponível", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
