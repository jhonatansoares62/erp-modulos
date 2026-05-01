package br.com.erpkit.consultas.service;

import br.com.erpkit.consultas.client.dto.EnderecoResponse;
import br.com.erpkit.consultas.service.provider.BrasilApiProvider;
import br.com.erpkit.consultas.service.provider.ProviderIndisponivelException;
import br.com.erpkit.consultas.service.provider.ViaCepProvider;
import br.com.erpkit.consultas.validation.DocumentoValidator;
import br.com.erpkit.shared.exception.ModuloException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CepService {

    private static final Logger log = LoggerFactory.getLogger(CepService.class);

    private final BrasilApiProvider brasilApi;
    private final ViaCepProvider viaCep;

    public CepService(BrasilApiProvider brasilApi, ViaCepProvider viaCep) {
        this.brasilApi = brasilApi;
        this.viaCep = viaCep;
    }

    @Cacheable(value = "cep", key = "#cepInput")
    public EnderecoResponse consultar(String cepInput) {
        String cep = DocumentoValidator.normalizarCep(cepInput);
        if (!DocumentoValidator.cepValido(cep)) {
            throw new ModuloException("CEP deve ter 8 dígitos numéricos");
        }

        Optional<EnderecoResponse> resultado;
        try {
            resultado = brasilApi.consultarCep(cep);
        } catch (ProviderIndisponivelException e) {
            log.info("BrasilAPI falhou pra CEP {}, caindo pra ViaCEP", cep);
            resultado = viaCep.consultarCep(cep);
        }

        if (resultado.isEmpty()) {
            try {
                resultado = viaCep.consultarCep(cep);
            } catch (ProviderIndisponivelException e) {
                throw new ModuloException("Todos os provedores indisponíveis", HttpStatus.SERVICE_UNAVAILABLE);
            }
        }

        return resultado.orElseThrow(() ->
                new ModuloException("CEP " + cep + " não encontrado", HttpStatus.NOT_FOUND));
    }
}
