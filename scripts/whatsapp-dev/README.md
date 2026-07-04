# Kit de teste local — api-whatsapp (sem Meta)

Roda o `api-whatsapp` na sua máquina e testa **inbound + outbound** sem conta Meta,
sem WABA e sem túnel. Usa H2 em memória e dois mocks locais (Meta falso + ERP falso).

## Pré-requisitos
- JDK 21 (`JAVA_HOME` = `C:\Program Files\Java\jdk21.0.10_7`)
- Python 3, `curl`, `openssl` (já vêm no Git Bash)

## Como rodar (3 terminais)

**Terminal 1 — mocks:**
```bash
python scripts/whatsapp-dev/mock-servers.py
# Meta-mock :9199  |  ERP-echo :9198
```

**Terminal 2 — o serviço (profile dev, H2, secrets dummy):**
```bash
cd /c/projetos/erp-modulos
export JAVA_HOME="/c/Program Files/Java/jdk21.0.10_7"
./mvnw -pl api-whatsapp -Pdev spring-boot:run -Dspring-boot.run.profiles=dev
# sobe em http://localhost:9193
```

**Terminal 3 — smoke test:**
```bash
bash scripts/whatsapp-dev/smoke.sh
```

## O que observar

No **Terminal 3** (respostas HTTP):
- `GET /health` → 200, `GET /swagger-ui.html` → 200
- `enviar-texto` / `enviar-botoes` → `{"wamid":"wamid.fake.N"}`
- telefone inválido → 400 · sem `X-API-Key` → 401
- webhook assinado → 200 · assinatura inválida → 401

No **Terminal 1** (mocks):
- `[META-MOCK] SEND MESSAGE` a cada envio outbound (mostra o JSON que o serviço
  mandaria pro WhatsApp Cloud API)
- `[ERP-ECHO] callback` ~1–2s após cada webhook entrante (mostra o
  `ComandoCallbackDTO` que o ERP receberia — telefone, comando, payload, etc.)

Explore os endpoints no navegador: <http://localhost:9193/swagger-ui.html>.

## Ajustar os cenários
Edite os JSON em `payloads/` (o `from` é o telefone do cliente; o `button_reply.id`
é o comando que o ERP receberia — ex. `confirmar_agendamento_42`). O `smoke.sh`
recalcula o HMAC automaticamente.

## Próximo nível — Meta real
Quando tiver uma conta WhatsApp Business, siga o **[`api-whatsapp/RUNBOOK.md`](../../api-whatsapp/RUNBOOK.md)**:
app Meta + WABA + token permanente + `subscribed_apps` + túnel Cloudflare. Aí é só
trocar os secrets dummy pelos reais (via env vars) e rodar sem o profile `dev`.
