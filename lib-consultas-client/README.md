# lib-consultas-client

Cliente HTTP + DTOs do módulo `api-consultas` (CEP/CNPJ via BrasilAPI).

## Uso

Adicione no `pom.xml` do ERP:

```xml
<dependency>
    <groupId>br.com.erpkit</groupId>
    <artifactId>lib-consultas-client</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

Configure no `application.yml`:

```yaml
app:
  modulos:
    consultas:
      enabled: true
      url: http://localhost:9192
      api-key: ${MODULO_CONSULTAS_API_KEY:}
      timeout: 5000
```

Injete e use:

```java
@Service
public class FornecedorService {
    private final ConsultasClient consultas;

    public FornecedorService(ConsultasClient consultas) { this.consultas = consultas; }

    public EnderecoResponse buscarCep(String cep) {
        return consultas.consultarCep(cep);
    }
}
```

O bean `ConsultasClient` só é criado quando `app.modulos.consultas.enabled=true`. Em dev sem o módulo `api-consultas` rodando, deixe `enabled=false` e o bean nem é registrado.

## Resiliência

- Circuit breaker (sliding window 10, threshold 50%, open 60s)
- Retry exponencial 3x (base 1s, multiplier 2)
- Timeout configurável

Erros: `ConsultasException` (4xx/5xx do servidor), `ConsultasIndisponivelException` (módulo offline / circuit breaker aberto).
