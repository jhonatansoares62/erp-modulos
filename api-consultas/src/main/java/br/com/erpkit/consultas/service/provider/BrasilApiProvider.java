package br.com.erpkit.consultas.service.provider;

import br.com.erpkit.consultas.client.dto.EnderecoResponse;
import br.com.erpkit.consultas.client.dto.FornecedorResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class BrasilApiProvider {

    private static final Logger log = LoggerFactory.getLogger(BrasilApiProvider.class);
    private static final String BASE_URL = "https://brasilapi.com.br/api";

    private final RestTemplate restTemplate;

    public BrasilApiProvider(RestTemplateBuilder builder,
                             @Value("${modulo.timeout-externo-ms:5000}") int timeoutMs) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public Optional<EnderecoResponse> consultarCep(String cep) {
        try {
            BrasilApiCep raw = restTemplate.getForObject(
                    BASE_URL + "/cep/v2/" + cep, BrasilApiCep.class);
            if (raw == null) return Optional.empty();

            EnderecoResponse e = new EnderecoResponse();
            e.setCep(raw.cep);
            e.setLogradouro(raw.street);
            e.setBairro(raw.neighborhood);
            e.setCidade(raw.city);
            e.setUf(raw.state);
            e.setProvedor("brasilapi");
            return Optional.of(e);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Falha BrasilAPI/CEP {}: {}", cep, e.getMessage());
            throw new ProviderIndisponivelException("brasilapi", e);
        }
    }

    public Optional<FornecedorResponse> consultarCnpj(String cnpj) {
        try {
            BrasilApiCnpj raw = restTemplate.getForObject(
                    BASE_URL + "/cnpj/v1/" + cnpj, BrasilApiCnpj.class);
            if (raw == null) return Optional.empty();

            FornecedorResponse f = new FornecedorResponse();
            f.setCnpj(raw.cnpj);
            f.setRazaoSocial(raw.razaoSocial);
            f.setNomeFantasia(raw.nomeFantasia);
            f.setSituacao(raw.descricaoSituacaoCadastral);
            f.setDataAbertura(parseData(raw.dataInicioAtividade));
            f.setNaturezaJuridica(raw.naturezaJuridica);
            f.setCnaePrincipalCodigo(raw.cnaeFiscal != null ? raw.cnaeFiscal.toString() : null);
            f.setCnaePrincipalDescricao(raw.cnaeFiscalDescricao);
            f.setEmail(raw.email);
            f.setTelefone(raw.ddd != null && raw.telefone != null ? raw.ddd + raw.telefone : raw.telefone);
            f.setProvedor("brasilapi");

            EnderecoResponse end = new EnderecoResponse();
            end.setCep(raw.cep);
            end.setLogradouro(montarLogradouro(raw));
            end.setNumero(raw.numero);
            end.setComplemento(raw.complemento);
            end.setBairro(raw.bairro);
            end.setCidade(raw.municipio);
            end.setUf(raw.uf);
            end.setProvedor("brasilapi");
            f.setEndereco(end);

            return Optional.of(f);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Falha BrasilAPI/CNPJ {}: {}", cnpj, e.getMessage());
            throw new ProviderIndisponivelException("brasilapi", e);
        }
    }

    private String montarLogradouro(BrasilApiCnpj raw) {
        if (raw.logradouro == null) return null;
        if (raw.descricaoTipoDeLogradouro != null && !raw.descricaoTipoDeLogradouro.isBlank()) {
            return (raw.descricaoTipoDeLogradouro + " " + raw.logradouro).trim();
        }
        return raw.logradouro;
    }

    private LocalDate parseData(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDate.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrasilApiCep {
        public String cep;
        public String state;
        public String city;
        public String neighborhood;
        public String street;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrasilApiCnpj {
        public String cnpj;
        @JsonProperty("razao_social") public String razaoSocial;
        @JsonProperty("nome_fantasia") public String nomeFantasia;
        @JsonProperty("descricao_situacao_cadastral") public String descricaoSituacaoCadastral;
        @JsonProperty("data_inicio_atividade") public String dataInicioAtividade;
        @JsonProperty("natureza_juridica") public String naturezaJuridica;
        @JsonProperty("cnae_fiscal") public Long cnaeFiscal;
        @JsonProperty("cnae_fiscal_descricao") public String cnaeFiscalDescricao;
        public String email;
        public String ddd;
        public String telefone;
        public String cep;
        @JsonProperty("descricao_tipo_de_logradouro") public String descricaoTipoDeLogradouro;
        public String logradouro;
        public String numero;
        public String complemento;
        public String bairro;
        public String municipio;
        public String uf;
        public List<Object> qsa;
    }
}
