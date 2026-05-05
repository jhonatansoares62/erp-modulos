# Feature Research

**Domain:** WhatsApp Cloud API integration — reactive-only ERP service module (api-whatsapp)
**Researched:** 2026-05-05
**Confidence:** HIGH — Meta official docs + verified cross-references

---

## Scope Constraint (Read This First)

This module is **REACTIVE PURE**. The client always initiates. The ERP only responds within the
24-hour customer service window. Templates are never used. Cost is architecturally guaranteed to be
zero by the absence of a template-send API, not by policy discipline.

All features below are categorized with this constraint as the primary filter.

---

## Feature Landscape

### Table Stakes (Must Have — Without These the Integration Does Not Work)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Webhook GET endpoint (hub.challenge) | Meta will not activate webhook without it | LOW | Return `hub.challenge` value if `hub.verify_token` matches; one-time per setup |
| Webhook POST endpoint with HMAC validation | Meta sends all events here; unauthenticated POST = security hole | MEDIUM | `X-Hub-Signature-256` header; HMAC-SHA256 of raw body with `appSecret`; reject non-matching with 403 |
| 200 OK in under 5 seconds | Meta retries if no 200; without this every message arrives multiple times | MEDIUM | Acknowledge first, process async; idempotency guard prevents double-processing on retries |
| Idempotency by `wamid` | Meta guarantees at-least-once delivery, not exactly-once | LOW | `wamid` UNIQUE constraint in `mensagens_log`; INSERT ... ON CONFLICT DO NOTHING |
| Parse incoming `text` message type | Core command channel — customer types "orcamento", "boleto", etc. | LOW | `entry[0].changes[0].value.messages[0].type == "text"` |
| Parse incoming `interactive/button_reply` | Customer taps APROVAR/RECUSAR buttons — the primary approval flow | LOW | `interactive.type == "button_reply"`, extract `button_reply.id` and `button_reply.title` |
| Parse incoming `interactive/list_reply` | Customer selects from a list menu | LOW | `interactive.type == "list_reply"`, extract `list_reply.id` |
| `clientes_zap` table — telefone → cliente_erp mapping | Without phone-to-client resolution, every message is anonymous | LOW | `telefone UNIQUE NOT NULL`, `id_cliente_erp FK`, Flyway V1 migration |
| `ultima_mensagem_em` update on every inbound | Required for 24h window enforcement | LOW | Update on every inbound message event; part of V4 `estado_conversa` migration |
| 24h window hard-block before any outbound | Architectural cost-zero guarantee; without this a bug in a handler can cause charges | LOW | Check `ultima_mensagem_em < now() - interval '24 hours'`; return 409 with structured log if window closed |
| Outbound: text message | Simplest response — status updates, confirmations, error messages | LOW | POST to `/{phoneNumberId}/messages` with `type: text` |
| Outbound: document (PDF upload + send) | Boleto, NF-e, orcamento — the core ERP documents | MEDIUM | Upload via multipart to `/media`, receive `media_id`, then send `type: document` with `id` field |
| Outbound: interactive button message (up to 3 buttons) | APROVAR/RECUSAR UX — far superior to asking customer to type | MEDIUM | `type: interactive`, `interactive.type: button`, `action.buttons[]` max 3 items |
| Outbound: interactive list message (up to 10 items) | Menu principal, lista de orcamentos abertos | MEDIUM | `type: interactive`, `interactive.type: list`, `action.sections[]` with `rows[]` max 10 total |
| Media cache by sha256 → media_id (TTL 30d) | `media_id` from Meta upload expires in ~30 days; same PDF (boleto, NF) uploaded daily without cache hits rate limits and wastes bandwidth | MEDIUM | `media_cache` table: `arquivo_hash sha256 PK`, `media_id`, `expira_em`; check before upload |
| `mensagens_log` with direction in/out | Audit trail, debugging, LGPD compliance evidence | LOW | `direcao CHAR(3)`, `tipo`, `conteudo`, `media_id`, `wamid`; store both inbound and outbound |
| `WhatsAppProperties` with fail-fast on boot | Missing `phoneNumberId`/`accessToken`/`appSecret` = silent failures at runtime | LOW | `@ConfigurationPropertiesBinding` + `@Validated` + `@NotBlank`; `@PostConstruct` validation |
| Delivery status webhook parsing (sent/delivered/read/failed) | Meta sends status callbacks for every outbound message; without parsing them, `wamid` in `mensagens_log` is orphaned and errors are invisible | MEDIUM | `entry.changes.value.statuses[]`; update `mensagens_log.status` field; log `failed` with `errors[].code` |
| Routing to ERP via callback HTTP | api-whatsapp must not contain business logic; SPI pattern requires forwarding parsed command to ERP | LOW | `POST {erpCallbackUrl}` with `{telefone, tipoComando, payload}`; configurable URL |
| Unrecognized client response | Phone not in `clientes_zap` → respond with "Numero nao cadastrado" text message | LOW | Avoids silent drops; prevents Meta from flagging number as unresponsive |
| Unknown command fallback | Message type or text not recognized → send menu again with "Nao entendi" prefix | LOW | Defensive routing; prevents webhook from returning 500 on unrecognized input |
| SpringDoc OpenAPI `/swagger-ui.html` | Consistent with existing modules; required by monorepo conventions | LOW | `springdoc-openapi-starter-webmvc-ui` already in monorepo stack |
| Flyway migrations in schema `whatsapp` | Consistent with api-email/api-storage pattern; auto-runs on boot | LOW | V1 clientes_zap, V2 mensagens_log, V3 media_cache, V4 estado_conversa |

### Differentiators (Nice-to-Have, Competitive Advantage Within Our Constraints)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Mark-as-read on webhook receipt | Shows blue double-check to customer immediately; signals ERP received the message; improves perceived responsiveness | LOW | POST `/{phoneNumberId}/messages` with `{"messaging_product":"whatsapp","status":"read","message_id":"<wamid>"}` — same endpoint as outbound messages |
| Typing indicator before slow responses | 25-second animated "..." in customer's WhatsApp while ERP processes; reduces "did it work?" anxiety | LOW | POST `/{phoneNumberId}/typing_indicators` (or statuses endpoint — confirm with current Meta docs); disappears on response or after 25s; only useful for handlers that take >1s |
| Structured error categorization in log | Error code 131047 (re-engagement message) vs 131049 (not delivered by Meta) vs 130429 (throughput exceeded) require different handling; flat "failed" logs are useless operationally | MEDIUM | Map Meta error codes to internal enum: RATE_LIMITED, UNREGISTERED_NUMBER, POLICY_BLOCKED, INTERNAL; log structured JSON |
| `GET /api/whatsapp/status` health endpoint | Consistent with api-email/api-storage; ERPKit RUNBOOK and monitoring depend on it | LOW | Return `{status, phoneNumberId, windowStats: {messagesLast24h, windowsOpen}}` |
| Message volume metrics endpoint | Detect abuse or unexpected charge triggers before they hit billing | MEDIUM | Aggregate count from `mensagens_log` by direction/type/hour; expose at `/api/whatsapp/metricas` |
| Phone number normalization on ingest | Meta sends numbers as `5546999999999` (no `+`); ERP may store `(46) 9 9999-9999`; normalize at ingest to avoid lookup misses | LOW | Strip `+`, spaces, dashes, parentheses; ensure country code prefix `55`; store normalized in `clientes_zap.telefone` |
| Graceful "window closed" user message | Instead of silent 409, optionally send a configured text via alternative channel (e-mail trigger to ERP) — but never a WhatsApp message without open window | MEDIUM | 409 response body includes `{windowClosedAt, telefone}` so ERP handler can decide to send SMS/e-mail; api-whatsapp itself does NOT send anything |
| Media upload retry with exponential backoff | Graph API `/media` endpoint occasionally returns 5xx; without retry, document sends fail silently during transient Meta outages | LOW | Resilience4j retry already in monorepo; apply to `MediaUploadService.upload()` |
| RUNBOOK.md for ERPKit provisioning | Without this, onboarding a new client requires tribal knowledge; this is the operational documentation | MEDIUM | Step-by-step: create WABA, generate System User token, configure webhook URL, Cloudflare Tunnel ingress, populate installer variables |

### Anti-Features (Deliberately NOT Building — Critical for Zero-Cost Guarantee)

| Feature | Why Requested | Why This Violates Our Constraints | What to Do Instead |
|---------|---------------|-----------------------------------|--------------------|
| Template message send API (`enviarTemplate()`) | "We need to send boleto reminders proactively" | Every template send outside 24h window is billed (marketing ~R$0.30, utility ~R$0.08 per message); having the method in code means one bug in a handler generates recurring charges | WhatsApp Cloud API `WhatsAppCloudClient` class simply does not implement `enviarTemplate()`. Method does not exist. If ERP needs to "remind" a customer, send SMS or e-mail asking them to initiate WhatsApp contact. |
| Proactive/scheduled outbound (event-triggered) | "When order is approved, push notification to customer" | ERP initiating message opens a business-initiated conversation = paid if no open window; creates dependency on window state that is hard to audit | ERP registers event handlers that RESPOND to commands. For notifications, send SMS/e-mail with a `wa.me` deeplink asking customer to message first. |
| Mass broadcast / bulk send | "Send promotion to all customers" | Marketing category: ~R$0.30 per message; 1000 customers = R$300/send; zero-cost guarantee broken immediately | Out of scope for this module entirely. Use `api-email` for marketing campaigns. |
| Automatic retry outside 24h window | "If window closed, queue and retry when customer messages again" | Queuing implies holding outbound intent; if customer never re-opens window, message is never sent but code complexity increases; if they do, it may arrive out of context | Hard-block at 409. ERP handler receives 409 and decides what to do (log it, notify vendor via e-mail, etc). No retry queue in api-whatsapp. |
| Template management CRUD (create/edit/approve) | "Would be useful to manage templates from within the ERP" | Invites template usage; once templates exist, handlers will use them; violates D4 | Not implemented. Template management stays in Meta Business Manager and is ERPKit's operational concern, not the developer's. |
| Multi-tenant routing within a single instance | "We could share one api-whatsapp instance across all clients" | Each client has their own `phoneNumberId` + `accessToken` + WABA; mixing them requires per-request credential dispatch, complex schema isolation, security boundary problems | One process per client. D1 (on-premise per client) enforces this by architecture. Multi-tenant is explicitly out of scope (see PROJECT.md). |
| Inbound media download and storage | "Store images/audio/video the customer sends" | Inbound media from customers can be images, audio, video, stickers — high storage burden; most are irrelevant to ERP workflows; media IDs expire; LGPD data minimization principle violated | Parse inbound message TYPE only. If type is not `text` or `interactive`, store the type name in log but do not download binary. Optionally log `media.id` for audit without fetching content. |
| Conversation state machine in api-whatsapp | "Track whether customer is in `aguardando_motivo_recusa` state" | State logic duplicates ERP business rules in the wrong module; api-whatsapp becomes coupled to each ERP's workflow; impossible to reuse across ERP-CALHAS | api-whatsapp stores only `ultima_mensagem_em`. All state lives in ERP handlers via `WhatsAppCommandHandler` SPI. Handlers use ERP's own database for state. |
| Push reaction emoji | "React with thumbs-up to confirmations" | Purely cosmetic; reactions are a Cloud API feature but add an extra HTTP call per message for zero business value | Not implemented in v1. If later added as differentiator it is LOW complexity (same `/messages` endpoint). |
| WhatsApp Flows (forms inside WhatsApp) | "Collect structured data from customer" | Requires additional Meta approval, complex JSON schema, and flows are business-initiated interactions that can generate costs | Not in scope. Use interactive list/button messages for structured choices. |
| Click-to-WhatsApp ad tracking | "Track which ad campaign drove the WhatsApp conversation" | Marketing attribution; irrelevant for ERP service channel; requires Meta Ads Manager integration | Out of scope. |
| AI/LLM natural language processing | "Parse free-text instead of keywords" | Adds external API dependency (cost), latency, non-deterministic behavior; ERP handlers need predictable inputs | Keyword normalization (lowercase, strip accents) is sufficient. SPI handlers receive normalized command strings. |

---

## Feature Dependencies

```
[Webhook POST endpoint + HMAC validation]
    └──requires──> [200 OK in <5s] (must acknowledge before processing)
    └──requires──> [Idempotency by wamid] (Meta retries; must handle duplicates)
    └──produces──> [Parse incoming text]
    └──produces──> [Parse incoming button_reply]
    └──produces──> [Parse incoming list_reply]

[Parse incoming text/button_reply/list_reply]
    └──requires──> [clientes_zap telefone → cliente_erp lookup]
    └──requires──> [ultima_mensagem_em update on inbound]
    └──produces──> [Routing to ERP via callback HTTP]

[Outbound: any message type]
    └──requires──> [24h window hard-block check]
    └──requires──> [WhatsAppProperties with phoneNumberId + accessToken]
    └──produces──> [mensagens_log out entry with wamid]

[Outbound: document]
    └──requires──> [Media cache by sha256] (check before upload)
    └──requires──> [Media upload retry] (transient 5xx from Meta)

[Delivery status webhook parsing]
    └──requires──> [mensagens_log with wamid] (need existing record to update)
    └──enhances──> [Structured error categorization] (failed status with error code)

[Mark-as-read]
    └──requires──> [wamid from inbound webhook] (need message ID to mark)
    └──depends-on──> [Same WhatsAppCloudClient HTTP client as outbound]

[Media cache]
    └──requires──> [mensagens_log] (media_id logged alongside outbound record)

[WhatsAppProperties fail-fast]
    └──blocks-all──> [All outbound features] (if misconfigured, fail at boot not at send time)

[Flyway migrations]
    └──required-by──> [clientes_zap, mensagens_log, media_cache, estado_conversa]

[24h window hard-block]
    └──requires──> [ultima_mensagem_em in clientes_zap/estado_conversa]
    └──conflicts-with──> [Template send API] (anti-feature; templates bypass the window concept)
    └──conflicts-with──> [Scheduled outbound] (anti-feature; would trigger outside window)
```

### Dependency Notes

- **24h window hard-block requires ultima_mensagem_em:** The check `ultima_mensagem_em > now() - 24h` is the architectural enforcement of zero cost. This field must be updated on every inbound event before any outbound logic runs.
- **Outbound document requires media cache:** Without the cache, the same PDF is uploaded every send, hitting the `/media` endpoint rate limits and adding ~200-500ms per send. Cache check must happen before upload attempt.
- **Delivery status parsing requires wamid in log:** The `status` webhook callback references the `wamid` returned at send time. The log entry must exist (inserted at send time) to be updated with final delivery status.
- **Mark-as-read conflicts with template anti-feature:** Both use the same `/messages` endpoint but mark-as-read uses `status: "read"` payload (not a message type that consumes template budget). Safe to implement.
- **Multi-tenant conflicts with all features:** All features assume single `phoneNumberId` + `accessToken` per process. Multi-tenant is an anti-feature because it would require per-request credential selection, breaking the simple property-injection pattern.

---

## MVP Definition

### Launch With (v1 — This Milestone)

- [x] Webhook GET hub.challenge verification
- [x] Webhook POST with HMAC X-Hub-Signature-256 validation
- [x] Idempotency by wamid (UNIQUE constraint + ignore duplicate)
- [x] Parse inbound: text, interactive/button_reply, interactive/list_reply
- [x] clientes_zap: telefone → cliente_erp resolution
- [x] ultima_mensagem_em update on every inbound
- [x] 24h window hard-block before any outbound (409 + structured log)
- [x] Outbound: text
- [x] Outbound: document (PDF upload + media cache by sha256 TTL 30d)
- [x] Outbound: interactive button (up to 3)
- [x] Outbound: interactive list (up to 10 items)
- [x] mensagens_log (in + out, with wamid)
- [x] Delivery status webhook parsing (update log status on sent/delivered/read/failed)
- [x] Routing to ERP via POST callback
- [x] WhatsAppProperties fail-fast on boot
- [x] Flyway migrations V1-V4
- [x] GET /api/whatsapp/status health endpoint
- [x] SpringDoc OpenAPI

### Add After Validation (v1.x — After First Client Pilot)

- [ ] Mark-as-read on inbound receipt — trigger: client feedback that messages feel "ignored"
- [ ] Typing indicator — trigger: handlers regularly take >2s to respond
- [ ] Structured error code categorization — trigger: first production incident with unclear failed status
- [ ] Phone number normalization — trigger: first lookup miss because ERP stored number in different format
- [ ] Message volume metrics endpoint — trigger: first billing scare or operational review

### Future Consideration (v2+ — After Product-Market Fit)

- [ ] Graceful window-closed notification (emit event to ERP instead of silent 409) — defer until ERP-CALHAS adoption reveals the need
- [ ] RUNBOOK automation (auto-provision WABA, generate System User token via Graph API) — deferred; D8 decision: manual provisioning is fine at low volume
- [ ] Inbound media type passthrough (log media ID without downloading) — only needed if ERP handlers start consuming media content
- [ ] Multi-ERP support (ERP-CALHAS handler registration) — handled by lib-whatsapp-client SPI; no api-whatsapp changes needed

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Webhook + HMAC | HIGH | MEDIUM | P1 |
| Idempotency wamid | HIGH | LOW | P1 |
| Parse text + button_reply + list_reply | HIGH | LOW | P1 |
| 24h window hard-block | HIGH | LOW | P1 |
| Outbound: text | HIGH | LOW | P1 |
| Outbound: document + media cache | HIGH | MEDIUM | P1 |
| Outbound: interactive button | HIGH | MEDIUM | P1 |
| Outbound: interactive list | HIGH | MEDIUM | P1 |
| mensagens_log | HIGH | LOW | P1 |
| Delivery status parsing | MEDIUM | MEDIUM | P1 |
| clientes_zap + routing callback | HIGH | LOW | P1 |
| WhatsAppProperties fail-fast | HIGH | LOW | P1 |
| Flyway migrations | HIGH | LOW | P1 |
| Status health endpoint | MEDIUM | LOW | P1 |
| SpringDoc OpenAPI | LOW | LOW | P1 (monorepo convention) |
| Mark-as-read | MEDIUM | LOW | P2 |
| Typing indicator | LOW | LOW | P2 |
| Structured error categorization | MEDIUM | MEDIUM | P2 |
| Phone number normalization | MEDIUM | LOW | P2 |
| Message volume metrics | MEDIUM | MEDIUM | P2 |
| Graceful window-closed event | LOW | MEDIUM | P3 |
| RUNBOOK automation | LOW | HIGH | P3 |
| [All anti-features] | N/A — prohibited | N/A | NEVER |

**Priority key:**
- P1: Must have for launch (this milestone)
- P2: Should have, add after first client pilot
- P3: Nice to have, future milestone
- NEVER: Anti-feature — explicitly prohibited

---

## Operational Features Detail

### Rate Limit Handling

Meta rate limits operate on two axes:

1. **Messaging tier limits** (unique users per rolling 24h): 250 (unverified) → 1,000 → 10,000 → 100,000 → unlimited. For our reactive model, tier limits apply only to BUSINESS-INITIATED messages. Customer-service-window replies (what we exclusively do) are NOT counted against the tier limit. In practice we will never hit this.

2. **Throughput (messages per second)**: Default 80 MPS. A single ERP client with reactive-only traffic will never approach this.

**Action:** Log HTTP 429 responses from Meta with error code 130429 (throughput) or 131031 (account locked). Resilience4j retry already covers transient 5xx. Do NOT implement exponential backoff for 429 in v1 — the volume does not justify it. Add it in v1.x if production data shows it is needed.

### Webhook Delivery Semantics

- Meta retries webhooks with exponential backoff for up to 7 days if no 200 is returned.
- After 7 days, events are discarded. There is no replay API or dead-letter queue.
- Consequence: the 200-in-5s + idempotency combo is the only protection against message loss AND duplicate processing.

### LGPD/GDPR Compliance (On-Premise Deployment)

Our on-premise deployment model (D1) is the strongest possible compliance posture:
- Conversation data never leaves the client's premises.
- No ERPKit server sees message content.
- Meta retains messages for up to 30 days for delivery only; we hold our own copy in `mensagens_log`.
- Business responsibilities as data controller: client must include WhatsApp in their privacy policy, obtain consent (implied by customer initiating contact), and handle deletion requests.
- `mensagens_log` retention: recommend configuring a Flyway-compatible purge job (not in v1 scope) to delete records older than 90 days; document this in RUNBOOK.md.
- `media_cache` TTL 30d already aligns with Meta's media expiry policy.

### Webhook Message Types to Parse vs. Ignore

We must parse:
- `text` — primary command channel
- `interactive` with `button_reply` — approval flow
- `interactive` with `list_reply` — menu selection

We must handle gracefully (acknowledge 200, log type, do NOT download binary, route to ERP with type-only payload so handler can respond appropriately):
- `image`, `video`, `audio`, `voice`, `document` (inbound) — customer sent a file; respond with "Recebemos sua mensagem" or route to handler
- `location` — ignore with generic response
- `sticker` — ignore with generic response
- `reaction` — ignore (no routing needed; already logged via status)
- `contacts` — ignore with generic response
- `order` — ignore (WhatsApp Shops feature, not relevant to our ERP model)
- `unsupported` — acknowledge 200, log, respond with "Formato nao suportado. Tente digitar um comando."

**Do NOT download binary content for any inbound media type.** Log `media.id` (string) for audit, not bytes.

---

## Sources

- [WhatsApp Cloud API — Guides: Send Messages](https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-messages/) — HIGH confidence (official)
- [Mark Messages as Read](https://developers.facebook.com/documentation/business-messaging/whatsapp/messages/mark-message-as-read/) — HIGH confidence (official)
- [Typing Indicators](https://developers.facebook.com/documentation/business-messaging/whatsapp/typing-indicators/) — HIGH confidence (official docs exist; endpoint exact payload verified via community sources)
- [WhatsApp Messaging Limits](https://developers.facebook.com/docs/whatsapp/messaging-limits/) — HIGH confidence (official)
- [Error Codes](https://developers.facebook.com/documentation/business-messaging/whatsapp/support/error-codes) — HIGH confidence (official)
- [Webhooks Reference: messages](https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/reference/messages) — HIGH confidence (official)
- [WhatsApp Pricing — Meta for Developers](https://developers.facebook.com/docs/whatsapp/pricing) — HIGH confidence (official)
- [GDPR and WhatsApp Solution Architectures](https://www.chatarchitect.com/news/gdpr-and-other-requirements-for-whatsapp-solution-architectures) — MEDIUM confidence (third-party, cross-referenced with official Meta data policy)
- [Rate Limits Guide — fyno.io](https://www.fyno.io/blog/whatsapp-rate-limits-for-developers-a-guide-to-smooth-sailing-clycvmek2006zuj1oof8uiktv) — MEDIUM confidence (third-party, cross-referenced with official messaging limits page)
- Source document: `C:\projetos\ERP-MUDAS\TEMP\zap\integracao-whatsapp-erp.md` (2026-05-01, 467 lines) — HIGH confidence (domain-specific, written against Cloud API v22.0)
- Source document: `C:\projetos\erp-modulos\PLANO-WHATSAPP.md` — HIGH confidence (project decisions already locked)

---

*Feature research for: api-whatsapp — WhatsApp Cloud API reactive-only ERP service module*
*Researched: 2026-05-05*
