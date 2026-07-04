# RUNBOOK — api-whatsapp

Operação e provisionamento do módulo WhatsApp em produção (on-premise por cliente).
Ordem recomendada: **Meta → tokens → servidor → webhook → smoke test**.

> ⚠️ **O passo mais esquecido é o #5 (`subscribed_apps`).** Sem ele, o webhook responde 200
> ao handshake, mas o Meta **nunca entrega** as mensagens entrantes ("shadow delivery"):
> tudo parece certo e nada chega. Se mensagens não chegam, comece por ali.

---

## 1. Criar o app Meta + WABA

1. Acesse <https://developers.facebook.com/apps> → **Create App** → tipo **Business**.
2. Adicione o produto **WhatsApp** ao app.
3. Em **WhatsApp → API Setup**, anote:
   - **Phone Number ID** → `WHATSAPP_PHONE_NUMBER_ID`
   - **WhatsApp Business Account ID** (WABA ID) → usado no passo 5
4. Em **App Settings → Basic**, copie o **App Secret** → `WHATSAPP_APP_SECRET`.

## 2. Gerar o token permanente (System User)

O token temporário da tela de setup expira em 24h — **não use em produção**.

1. <https://business.facebook.com> → **Business Settings → Users → System Users**.
2. Crie um System User com papel **Admin**.
3. **Add Assets** → atribua a WABA ao System User com permissão total.
4. **Generate New Token** → selecione o app → **expiração: `Never`**.
5. Marque os escopos `whatsapp_business_messaging` e `whatsapp_business_management`.
6. Copie o token → `WHATSAPP_ACCESS_TOKEN`.

## 3. Definir as variáveis de ambiente

No serviço WinSW (ou `.env` do instalador), defina os 5 secrets obrigatórios:

```
WHATSAPP_PHONE_NUMBER_ID=<phone number id>
WHATSAPP_ACCESS_TOKEN=<token System User "Never">
WHATSAPP_APP_SECRET=<app secret>
WHATSAPP_VERIFY_TOKEN=<string aleatória escolhida por você>
WHATSAPP_ERP_CALLBACK_URL=http://localhost:8080   # base do ERP no host
API_KEY=<chave dos endpoints internos>
```

`WHATSAPP_VERIFY_TOKEN` é qualquer string secreta que você inventa — ela precisa **casar**
com o valor digitado no painel do Meta no passo 4. O boot **falha** se algum dos 5 secrets
faltar.

## 4. Configurar a URL do webhook

O Meta precisa alcançar `POST https://<publico>/webhook/whatsapp`. Em on-premise, exponha o
`localhost:9193` via um túnel (ex: **Cloudflare Tunnel**):

```bash
cloudflared tunnel --url http://localhost:9193
# → https://<algo>.trycloudflare.com
```

No painel **WhatsApp → Configuration → Webhook**:
- **Callback URL:** `https://<algo>.trycloudflare.com/webhook/whatsapp`
- **Verify token:** o mesmo valor de `WHATSAPP_VERIFY_TOKEN`
- **Verify and save** — o Meta faz `GET .../webhook/whatsapp?hub.mode=subscribe&...` e espera
  o echo de `hub.challenge`.
- Em **Webhook fields**, assine **`messages`**.

Teste o handshake manualmente:

```bash
curl "https://<algo>.trycloudflare.com/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=<VERIFY_TOKEN>&hub.challenge=42"
# deve responder exatamente: 42   (plain text, sem aspas)
```

## 5. ⚠️ Verificar `subscribed_apps` (passo obrigatório — shadow delivery)

Assinar os *webhook fields* no painel **não basta**. O app precisa estar inscrito **na WABA**.
Confirme:

```bash
curl "https://graph.facebook.com/v22.0/<WABA_ID>/subscribed_apps" \
  -H "Authorization: Bearer <WHATSAPP_ACCESS_TOKEN>"
```

Se a lista vier **vazia**, inscreva o app:

```bash
curl -X POST "https://graph.facebook.com/v22.0/<WABA_ID>/subscribed_apps" \
  -H "Authorization: Bearer <WHATSAPP_ACCESS_TOKEN>"
# resposta esperada: {"success": true}
```

Repita o `GET` e confirme que o app aparece. **Sem isto, mensagens entrantes não chegam.**

## 6. Smoke test end-to-end

1. Envie uma mensagem do **seu WhatsApp pessoal** para o número da WABA (o cliente sempre
   inicia — modelo reativo).
2. Confira o log do serviço: deve registrar o webhook recebido e o callback ao ERP.
3. Verifique a persistência:
   ```sql
   SELECT wamid, telefone, tipo, direcao, criado_em
   FROM whatsapp.mensagens_log ORDER BY criado_em DESC LIMIT 5;
   ```
4. Teste o outbound (dentro da janela de 24h após o passo 1):
   ```bash
   curl -X POST http://localhost:9193/api/whatsapp/enviar-texto \
     -H "X-API-Key: <API_KEY>" -H "Content-Type: application/json" \
     -d '{"telefone":"55DDDNUMERO","texto":"Recebido, obrigado!"}'
   # → {"wamid":"..."}
   ```

---

## Troubleshooting

| Sintoma | Causa provável | Ação |
|---------|----------------|------|
| Handshake `GET` retorna 403 | `verify_token` diverge | Alinhe `WHATSAPP_VERIFY_TOKEN` com o painel Meta |
| `POST` webhook retorna 401 | Assinatura HMAC inválida | Confira `WHATSAPP_APP_SECRET` (é o **App Secret**, não o token) |
| Handshake OK mas **nada chega** | `subscribed_apps` vazio | Refaça o **passo 5** (POST subscribed_apps) |
| Envio retorna `409 JANELA_24H_FECHADA` | Fora da janela de 24h | Correto por design — só responda dentro de 24h da última msg do cliente |
| Envio retorna `422` com `metaErrorCode` | Erro do Meta (número inválido, etc.) | Veja o `metaErrorCode` no corpo da resposta |
| Boot falha na inicialização | Falta um dos 5 secrets | Confira as env vars do passo 3 (fail-fast) |
| `GET /api/whatsapp/status` mostra `circuitBreakerState=OPEN` | Cloud API instável | Circuit breaker abriu; aguarda 60s e testa half-open |

## Referência rápida

- Status/diagnóstico: `GET http://localhost:9193/api/whatsapp/status` (com `X-API-Key`)
- Health: `GET http://localhost:9193/health`
- Swagger: `http://localhost:9193/swagger-ui.html`
- Token do System User: escopo `whatsapp_business_messaging` + `whatsapp_business_management`, expiração `Never`
- Versão do Graph API: `v22.0` (`WHATSAPP_META_API_BASE_URL`)
