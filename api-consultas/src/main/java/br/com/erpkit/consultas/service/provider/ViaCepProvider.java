package br.com.erpkit.consultas.service.provider;

import br.com.erpkit.consultas.client.dto.EnderecoResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

@Component
public class ViaCepProvider {

    private static final Logger log = LoggerFactory.getLogger(ViaCepProvider.class);
    private static final String BASE_URL = "https://viacep.com.br/ws";

    private final RestTemplate restTemplate;

    public ViaCepProvider(RestTemplateBuilder builder,
                          @Value("${modulo.timeout-externo-ms:5000}") int timeoutMs) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public Optional<EnderecoResponse> consultarCep(String cep) {
        try {
            ViaCepRaw raw = restTemplate.getForObject(BASE_URL + "/" + cep + "/json/", ViaCepRaw.class);
            if (raw == null || Boolean.TRUE.equals(raw.erro)) return Optional.empty();

            EnderecoResponse e = new EnderecoResponse();
            e.setCep(raw.cep);
            e.setLogradouro(raw.logradouro);
            e.setComplemento(raw.complemento);
            e.setBairro(raw.bairro);
            e.setCidade(raw.localidade);
            e.setUf(raw.uf);
            e.setIbge(raw.ibge);
            e.setProvedor("viacep");
            return Optional.of(e);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Falha ViaCEP {}: {}", cep, e.getMessage());
            throw new ProviderIndisponivelException("viacep", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ViaCepRaw {
        public String cep;
        public String logradouro;
        public String complemento;
        public String bairro;
        public String localidade;
        public String uf;
        public String ibge;
        public Boolean erro;
    }
}
