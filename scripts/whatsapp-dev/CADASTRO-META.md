# Checklist — Cadastro do api-whatsapp na Meta + Cloudflare (Modo 2 / real)

Onboarding do módulo WhatsApp com número de teste da Meta + túnel Cloudflare nomeado
(mesmo padrão do ERP-Mudas/Calhas). Complementa o `../../api-whatsapp/RUNBOOK.md`.

> 🔑 **Verify token gerado (use nos dois lados — Meta e serviço):**
> `odonto_bc1d1408e8c753b01f92aed0`

> ⚠️ **Não commitar este arquivo com secrets preenchidos** (token de acesso, app secret,
> token do túnel). Deixe os valores só na sua máquina.

---

## 📋 Valores a coletar (preencher conforme avança)

| Valor | Onde pegar | Preenchido |
|-------|-----------|:----------:|
| Phone Number ID | Meta → WhatsApp → API Setup | ✅ `1126647363875421` |
| WABA ID (conta WhatsApp Business) | Meta → WhatsApp → API Setup | ✅ `2235521357242204` |
| Token de acesso (temporário 24h) | Meta → WhatsApp → API Setup | ✅ coletado na sessão (não commitar) |
| App Secret | Meta → Configurações do app → Básico | `__________` |
| Token do túnel (`eyJ...`) | Cloudflare → Tunnels → conector | `__________` |
| Hostname público | Cloudflare → Public Hostname | `https://__________` |

> Número de destino verificado no teste: `5546920009012` (seu WhatsApp).
> Graph API: `v25.0`. Teste `hello_world` → **200 accepted** ✅

---

## Frente A — Meta (console)

- [x] **A1.** Criar app em https://developers.facebook.com/apps → **Criar app** → tipo **Empresa/Business** → nome ex. `ERP Odonto WhatsApp`
- [x] **A2.** Adicionar o produto **WhatsApp** (card **WhatsApp → Configurar**) — cria número de teste + WABA de teste automaticamente
- [~] **A3.** Em **WhatsApp → Configuração da API (API Setup)**, copiar:
  - [x] 📋 **Phone Number ID** = `1126647363875421`
  - [x] 📋 **WABA ID** = `2235521357242204`
  - [x] 📋 **Token de acesso temporário** (24h)
- [x] **A4.** **App Secret** coletado (App ErpKit, ID 2500179087062236)
- [x] **A5.** No **API Setup**, campo **Para (To)** → seu WhatsApp `5546920009012` adicionado/verificado (teste `hello_world` chegou)

## Frente B — Cloudflare (mesmo padrão do Mudas)

- [ ] **B1.** https://one.dash.cloudflare.com → **Networks → Tunnels**
- [ ] **B2.** Criar túnel novo (ex. `erp-odonto-whatsapp`) ou reusar um existente e adicionar um **Public Hostname**:
  - Subdomain: ex. `whatsapp-odonto` · Domain: o de vocês · Path: (vazio)
  - Service: **HTTP** → `localhost:9193`
- [ ] **B3.** Copiar 📋 **token do túnel** (`eyJ...` do comando `cloudflared ... run --token ...`)
- [ ] **B4.** Anotar 📋 **hostname público** escolhido

---

## Frente C — Subir túnel + serviço (**eu faço**, com os valores acima)

- [ ] **C1.** Rodar o túnel: `cloudflared.exe tunnel run --token <TOKEN>` (binário reusado do ERP-Mudas)
- [ ] **C2.** Configurar o `api-whatsapp` com os secrets reais (Phone Number ID, token, App Secret, verify token) e subir na porta 9193
- [ ] **C3.** Confirmar `GET https://<hostname>/health` → 200 (túnel → serviço ok)

## Frente D — Ligar o webhook na Meta

- [ ] **D1.** Meta → **WhatsApp → Configuração → Webhook → Editar**:
  - Callback URL: `https://<hostname>/webhook/whatsapp`
  - Verify token: `odonto_bc1d1408e8c753b01f92aed0`
  - **Verificar e salvar** (Meta chama o handshake → nosso serviço ecoa o `hub.challenge`)
- [ ] **D2.** Em **Webhook fields**, assinar o campo **`messages`**
- [ ] **D3.** (eu) Verificar `subscribed_apps` — passo obrigatório contra *shadow delivery*:
  - `GET /{WABA_ID}/subscribed_apps` deve listar o app; se vazio, `POST` para inscrever

## Frente E — Teste ponta a ponta real

- [ ] **E1.** Do seu WhatsApp pessoal, enviar uma mensagem **para o número de teste** da Meta
- [ ] **E2.** (eu) Confirmar nos logs: webhook recebido + persistência + callback
- [ ] **E3.** (eu) Responder via `POST /api/whatsapp/enviar-texto` (dentro da janela de 24h) → chega no seu WhatsApp ✅

---

## Depois (produção)

- [ ] Trocar o token temporário pelo **token permanente** (System User, expiração `Never`) — ver `RUNBOOK.md` passo 2
- [ ] (Opcional) Migrar do número de teste para o **número real do consultório** (exige verificação de negócio)
- [ ] Rodar o túnel como **serviço WinSW** (padrão Mudas: `service-config-cloudflare.xml`)
