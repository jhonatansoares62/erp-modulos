# lib-whatsapp-client

Starter Spring Boot para consumir o módulo `api-whatsapp` a partir de um ERP: cliente HTTP
com Resilience4j + SPI de handlers de comando. Espelha o padrão de `lib-consultas-client`.

## Uso

Adicione no `pom.xml` do ERP:

```xml
<dependency>
    <groupId>br.com.erpkit</groupId>
    <artifactId>lib-whatsapp-client</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

Configure no `application.yml`:

```yaml
app:
  modulos:
    whatsapp:
      enabled: true
      url: http://localhost:9193
      api-key: ${MODULO_WHATSAPP_API_KEY:}   # opcional; enviado como header X-API-Key quando não-blank
      timeout: 5s
```

O bean `WhatsAppClient` (e o `WhatsAppCommandRegistry`) só são criados quando
`app.modulos.whatsapp.enabled=true`. Em dev sem o `api-whatsapp` rodando, deixe
`enabled=false` e nenhum bean é registrado.

## Enviar mensagens (outbound)

```java
@Service
public class NotificacaoService {
    private final WhatsAppClient whatsapp;

    public NotificacaoService(WhatsAppClient whatsapp) { this.whatsapp = whatsapp; }

    public void avisarOrcamento(String telefone, byte[] pdf) {
        whatsapp.enviarTexto(telefone, "Seu orçamento está pronto!");
        whatsapp.enviarDocumento(telefone, pdf, "orcamento.pdf", "application/pdf", "Orçamento #123");
        whatsapp.enviarBotoes(telefone, "Deseja aprovar?", List.of(
                new BotaoDto("aprovar", "Aprovar"),
                new BotaoDto("recusar", "Recusar")));
    }
}
```

> ⚠️ **Custo zero de Meta por design:** só é possível responder dentro da janela de 24h
> (a trava vive no `api-whatsapp`, que retorna `409 JANELA_24H_FECHADA` fora dela). Não
> existe método de envio de *template* — impossível gerar custo por conversa iniciada.

## Receber comandos (inbound) — SPI

O `api-whatsapp` faz callback ao ERP quando o cliente envia uma mensagem. Para tratar um
comando, declare um bean que implemente `WhatsAppCommandHandler`:

```java
@Component
public class OrcamentoHandler implements WhatsAppCommandHandler {

    @Override
    public String getComando() { return "orcamento"; }

    @Override
    public WhatsAppRespostaDto processar(WhatsAppComandoDto comando) {
        // ... lógica do ERP ...
        return WhatsAppRespostaDto.texto("Recebido! Vamos preparar seu orçamento.");
    }
}
```

O `WhatsAppCommandRegistry` coleta todos os handlers do contexto e roteia o comando
entrante:

1. **Match exato** (`O(1)`) por `getComando()` (case-insensitive).
2. **Fallback por prefixo** — sobrescreva `matches()` para comandos com argumento
   (ex: `"aprovar 1234"` casa com o handler `"aprovar"`):

```java
@Override
public boolean matches(String comando) {
    return comando != null && comando.toLowerCase().startsWith("aprovar");
}
```

Em colisão de match exato, o **primeiro handler registrado vence** — use `@Order` para
controlar a precedência. No seu controller de callback, resolva e despache:

```java
whatsAppCommandRegistry.resolver(cmd.comando())
        .map(h -> h.processar(cmd))
        .ifPresent(resposta -> whatsAppClient.despachar(cmd.telefone(), resposta));
```

## API

| Método | Descrição |
|--------|-----------|
| `enviarTexto(telefone, texto)` | Mensagem de texto |
| `enviarDocumento(telefone, bytes, filename, mimeType, caption)` | Documento (base64 internamente) |
| `enviarBotoes(telefone, texto, botoes)` | Até 3 botões |
| `enviarLista(telefone, texto, secoes)` | Até 10 itens somando as seções |
| `despachar(telefone, resposta)` | Despacha uma `WhatsAppRespostaDto` pelo tipo |
| `status()` | Proxy do `GET /api/whatsapp/status` |
| `isOnline()` / `isHabilitado()` / `getCircuitBreakerState()` | Diagnóstico |

## Resiliência

- Circuit breaker (sliding window 10, threshold 50%, open 60s)
- Retry exponencial 3x (base 1s, multiplier 2) — 4xx **não** são retentados
- Timeout configurável (`app.modulos.whatsapp.timeout`)

Erros: `WhatsAppException` (4xx/5xx do `api-whatsapp`, com `getStatus()`),
`WhatsAppIndisponivelException` (circuit breaker aberto, conexão recusada, timeout esgotado
ou módulo desabilitado).
