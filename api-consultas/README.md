# api-consultas

Módulo plugável de consultas externas (porta default 9192).

## Endpoints

```
GET  /health                  → {"status":"UP","modulo":"consultas","versao":"..."}
GET  /api/info                → metadados
GET  /api/cep/{cep}           → EnderecoResponse (200) | 404 | 422
GET  /api/cnpj/{cnpj}         → FornecedorResponse (200) | 404 | 422
```

CEP/CNPJ aceitam máscaras (`01310-100`, `00.000.000/0001-91`) — o módulo normaliza.

## Provedores

- **CEP**: BrasilAPI primário → ViaCEP fallback automático
- **CNPJ**: BrasilAPI

## Cache

Caffeine in-memory:
- `cep`: TTL 7 dias, max 10k entradas
- `cnpj`: TTL 30 dias, max 10k entradas

## Rodar local

```bash
./mvnw spring-boot:run
curl http://localhost:9192/api/cep/01310100
```

## Configuração

```yaml
server:
  port: 9192
modulo:
  api-key: ${API_KEY:}        # se setada, exige header X-API-Key
  timeout-externo-ms: 5000
```
