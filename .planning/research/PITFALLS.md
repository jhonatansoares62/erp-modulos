# Pitfalls Research

**Domain:** WhatsApp Cloud API integration — api-whatsapp + lib-whatsapp-client (Spring Boot 3.5.9 / Java 21)
**Researched:** 2026-05-05
**Confidence:** HIGH (verified against Meta official docs, community post-mortems, Spring Framework docs)

---

## Severity Legend

- **P0** — Breaks zero-cost guarantee or causes silent billing. Fix before any message is sent.
- **P1** — Breaks reliability, causes message loss, or creates security vulnerability.
- **P2** — Quality degradation, operational friction, or silent failure with low blast radius.

---

## Critical Pitfalls

### Pitfall C-01: Non-template free-form messages sent outside 24h window trigger Error 131047 — or worse, get quietly reclassified

**Severity:** P0

**What goes wrong:**
Interactive reply-button messages (`type: interactive`, `type: button_reply`) and plain text messages are free-form (non-template) messages. They are ONLY allowed within an open 24-hour Customer Service Window (CSW). If the ERP handler tries to respond to an old message (e.g., a webhook retry delivered hours later, a race condition in the 24h check, or a bug in handler code), one of two outcomes occurs:

1. Meta returns HTTP 400 with **error 131047** ("Re-engagement message — customer messaged more than 24h ago"), which is the safe outcome because the send is explicitly rejected.
2. Worse: if the window is checked against a stale `ultima_mensagem_em` value (not yet committed, read from a replica lag, or checked before the webhook-processing transaction committed), the guard passes, the free-form message is sent, and Meta silently processes it — potentially counting it as a session conversation that DOES get billed if Meta reclassifies it.

**Why it happens:**
The 24h check (WHATS-13) reads `ultima_mensagem_em` from the database. If the check runs inside a transaction that hasn't committed the update from the incoming webhook (WHATS-04) yet, the guard sees a stale timestamp and allows an outbound send that should be blocked. This is a classic TOCTOU (time-of-check, time-of-use) race when webhook processing and outbound sending share the same transactional boundary.

**How to avoid:**
- Execute the 24h guard as a separate committed read (outside the webhook-processing transaction) immediately before calling `WhatsAppCloudClient`.
- In `WhatsAppCloudClient`, always persist the `ultima_mensagem_em` update with `@Transactional(propagation = REQUIRES_NEW)` so it commits before the outbound path can read it.
- Add a unit test that calls the guard with `ultima_mensagem_em = now() - 23h59m` (should allow) and `now() - 24h01m` (should reject with 409). Both cases must be tested with fresh DB reads, not mocked stale values.
- Reference: WHATS-13 requires the guard to reject with 409 + structured log. The 409 response to the ERP caller must include the last-message timestamp so the ERP can surface it to the operator.

**Warning signs:**
- Any `131047` error in outbound logs means the guard fired correctly — but investigate WHY the window was closed (was it a stale timestamp read?).
- Outbound sends succeeding at timestamps clustered around the 24h boundary (within ±5 minutes) warrant a WARN-level log for audit.
- A Meta invoice showing any line item for a Brazil phone number is a P0 alert.

**Phase to address:** Phase implementing WHATS-04 + WHATS-13 (webhook persistence + 24h guard). Must be tested together, never in isolation.

---

### Pitfall C-02: HMAC validation using ContentCachingRequestWrapper — body is empty at filter time

**Severity:** P0 (security bypass allowing fake webhooks to inject arbitrary commands)

**What goes wrong:**
Spring's `ContentCachingRequestWrapper` is a lazy wrapper — it only populates `getContentAsByteArray()` AFTER the request body has been consumed downstream (by the controller's `@RequestBody`). If the HMAC filter calls `getContentAsByteArray()` before `doFilter()`, it gets an empty byte array. The HMAC of an empty array never matches the signature, causing one of two bugs depending on implementation: (a) all real webhooks are rejected (503/400), or (b) the developer "fixes" the empty-array problem by skipping validation when bytes are empty — silently accepting any unauthenticated POST.

**Why it happens:**
The Spring documentation for `ContentCachingRequestWrapper` states the cache is populated only "as content is being read." Developers read about this class as the solution to "read body twice" and assume it caches eagerly. It does not.

**How to avoid:**
Use a custom `HttpServletRequestWrapper` that eagerly reads all bytes into a `ByteArrayInputStream` at construction time:

```java
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream() {
        return new DelegatingServletInputStream(new ByteArrayInputStream(cachedBody));
    }

    public byte[] getCachedBody() { return cachedBody; }
}
```

The HMAC filter wraps the request in this class FIRST (highest filter precedence), then performs `MessageDigest.isEqual(computedHmac, receivedHmac)` — using `MessageDigest.isEqual` (constant-time, from `java.security`) not `.equals()` or `Arrays.equals()`.

The filter must have `@Order(Ordered.HIGHEST_PRECEDENCE)` so no other filter or Spring Security processing touches the body first.

**Warning signs:**
- HMAC validation always fails in tests despite a correct secret → likely using `ContentCachingRequestWrapper` eagerly.
- HMAC validation passes for empty payloads → critical bug: empty-array shortcut.
- Any test where the HMAC filter is ordered AFTER `HttpMessageConverter` or `@RequestBody` parsing.

**Phase to address:** Phase implementing WHATS-01 (webhook receiver). Must be the first thing built, not added later as an afterthought.

---

### Pitfall C-03: HMAC comparison using string `.equals()` — timing attack vulnerability

**Severity:** P1 (security — timing oracle allows brute-force of App Secret)

**What goes wrong:**
The naive Java implementation computes the HMAC as a hex string and compares with `computedHex.equals(receivedHex)`. String `.equals()` short-circuits on the first mismatched character, leaking timing information that allows an attacker to progressively determine the correct HMAC byte-by-byte.

**Why it happens:**
Developers familiar with `String.equals()` don't realize it's not constant-time. The official Meta docs don't enforce a specific comparison method.

**How to avoid:**
Always use `MessageDigest.isEqual(byte[], byte[])` from `java.security`. This performs a constant-time XOR comparison:

```java
byte[] computed = hmacSha256(appSecret, rawBody);
byte[] received = Hex.decodeHex(signatureHeader.replace("sha256=", ""));
if (!MessageDigest.isEqual(computed, received)) {
    response.sendError(403);
    return;
}
```

Do NOT use `Arrays.equals()` — it is also not constant-time in all JVM implementations.

**Warning signs:**
- Any HMAC comparison written as `computedString.equals(headerString)`.
- Security audit flags timing-sensitive comparison.

**Phase to address:** Phase implementing WHATS-01. Include in the HMAC unit test (QA-01): test with a 1-byte-off signature, verify rejection, benchmark comparison time variance (should be negligible).

---

### Pitfall C-04: Webhook payload contains Unicode — HMAC uses wrong charset

**Severity:** P1 (HMAC validation silently fails for all messages with accented characters)

**What goes wrong:**
Meta computes the HMAC over the raw UTF-8 bytes of the JSON body. If the Spring filter converts the body bytes to a `String` (using the JVM default charset or `StandardCharsets.ISO_8859_1`) and then calls `getBytes()` to recompute the HMAC, the resulting bytes differ from Meta's bytes for any message containing characters outside ASCII (Portuguese accents: `ã`, `é`, `ç`, `ó`, etc.). The HMAC never matches → all such webhooks are rejected.

**Why it happens:**
`HttpServletRequest.getReader()` uses the request's declared charset (often ISO-8859-1 by default in Servlet spec). Developers who read the body as a String intermediate representation instead of raw bytes hit this silently because ASCII-only test payloads pass fine.

**How to avoid:**
Never convert the body to a String intermediate. Always work with `byte[]` from `getInputStream()` directly. The `CachedBodyHttpServletRequest` above captures raw bytes — use `cachedBody` directly for HMAC computation, never `new String(cachedBody).getBytes()`.

**Warning signs:**
- Unit tests pass (ASCII payloads) but production webhooks from Brazilian users are rejected (Portuguese text in messages).
- HMAC failure rate increases with message length / character diversity.
- Log pattern: `HMAC validation failed` immediately after a message containing `ã` or `ç`.

**Phase to address:** Phase implementing WHATS-01. Add a WireMock integration test (QA-02) with a payload containing `"Olá, gostaria de um orçamento"` and verify HMAC validation passes.

---

### Pitfall C-05: Synchronous webhook processing causes Meta retry storms

**Severity:** P1 (message duplication, cascade of replayed webhooks, database UNIQUE violations flooding logs)

**What goes wrong:**
If the webhook POST handler performs database writes, calls the ERP callback (WHATS-06), or does any I/O synchronously before returning 200, it may exceed Meta's 5-second timeout. Meta then retries the same webhook. Under normal load this is fine; during any slowdown (ERP callback slow, DB contention, GC pause) the retry snowball grows. Each retry is a duplicate wamid arriving, causing UNIQUE constraint violations on `mensagens_log.wamid` if idempotency is via DB constraint — which is correct behavior but generates noisy error logs and wastes DB connections.

Additionally: if the webhook processing takes >5s and returns 500 (not 200), Meta will retry for up to 7 days with exponential backoff. If the Cloudflare Tunnel reconnects after a gap, all queued retries arrive simultaneously.

**Why it happens:**
The ERP callback `POST http://localhost:8090/api/modulos/whatsapp/comando` is a synchronous HTTP call within the webhook handler. If the ERP is processing a heavy DB query when that arrives, the webhook handler blocks.

**How to avoid:**
Pattern: "ack first, process later."

```
1. Validate HMAC signature (synchronous, fast — just bytes comparison)
2. Check wamid idempotency (synchronous DB read — fast indexed lookup)
3. Persist incoming message (synchronous — single INSERT)
4. Return HTTP 200
5. Publish event to internal ApplicationEventPublisher (async)
6. Async listener: update ultima_mensagem_em + identify client + call ERP callback
```

This keeps the synchronous path to <100ms (HMAC + index lookup + INSERT) and guarantees 200 before the 5s timeout. Use Spring's `@Async` or `ApplicationEventPublisher` with `@TransactionalEventListener(phase = AFTER_COMMIT)` for the async fan-out.

**Warning signs:**
- Any webhook handler that calls `erpCallbackClient.enviarComando(...)` inside the same `@Transactional` method that returns the response.
- Response times on `/webhook/whatsapp` P95 > 1s.
- Duplicate wamid UNIQUE violation errors in logs.
- Meta retries same wamid 3+ times (visible in webhook logs with same `messages[0].id`).

**Phase to address:** Phase implementing WHATS-01 through WHATS-06. Architecture decision must be made at WHATS-01 time, not retrofitted.

---

### Pitfall C-06: wamid idempotency only on `mensagens_log` INSERT — race condition with concurrent deliveries

**Severity:** P1 (duplicate ERP callback dispatches for same customer message)

**What goes wrong:**
If two webhook deliveries for the same wamid arrive within milliseconds (Meta occasionally delivers to multiple subscribers, or a retry overlaps with the original), both requests pass the idempotency check (SELECT shows not-found), both proceed to INSERT, one succeeds, one gets a UNIQUE violation. The problem: if the ERP callback dispatch happens BEFORE the INSERT (or asynchronously in parallel), BOTH deliveries may fire the callback before either commits.

**Why it happens:**
A SELECT-then-INSERT idempotency pattern without a database-level lock has a TOCTOU window. The UNIQUE constraint catches the duplicate INSERT but cannot prevent both requests from reaching the callback dispatch code.

**How to avoid:**
Use `INSERT ... ON CONFLICT (wamid) DO NOTHING` (PostgreSQL) and check the number of affected rows. Only dispatch the ERP callback if `affectedRows == 1`:

```java
int inserted = mensagensLogRepository.insertIfAbsent(wamid, ...); // uses ON CONFLICT DO NOTHING
if (inserted == 1) {
    eventPublisher.publishEvent(new MensagemRecebidaEvent(mensagem));
}
```

This is atomic at the database level and eliminates the TOCTOU window. The UNIQUE constraint on `wamid` (WHATS-02) is the safety net, but `ON CONFLICT DO NOTHING` + row-count check is the primary guard.

**Warning signs:**
- ERP receives duplicate callbacks for same customer message within seconds of each other (same phone + same timestamp + same text).
- `DataIntegrityViolationException` on `wamid` UNIQUE constraint in logs — this is expected but should trigger a metric increment, not swallowed silently.

**Phase to address:** Phase implementing WHATS-02 + WHATS-03. The migration V2 must define `wamid VARCHAR(128) UNIQUE` and the service layer must use `ON CONFLICT DO NOTHING`.

---

### Pitfall C-07: media_id cache TTL of 30 days is correct but reuse-on-expired causes silent 400

**Severity:** P2 (PDF not delivered to customer, ERP gets 409 from api-whatsapp but no clear error)

**What goes wrong:**
The `media_cache` table stores `(arquivo_hash sha256, media_id, expira_em)` with TTL of 30 days (WHATS-11). When `expira_em` is past, the service should detect stale cache and re-upload. If the TTL check is off-by-one (using `>=` vs `>`) or if the cache entry is not evicted and the `media_id` is used anyway, Meta returns HTTP 400 / error 131053 for invalid media_id. The outbound send fails but the `mensagens_log` row may have already been inserted with direction `out`.

**Why it happens:**
Developers test media caching with a fresh upload and don't test the expiry path. The TTL is 30 days, so the bug is invisible during development.

**How to avoid:**
- In `MediaCacheService`, the check is `expira_em.isAfter(LocalDateTime.now())` (exclusive — must be strictly in the future). On cache miss OR expiry, re-upload and update `expira_em = now().plusDays(30)`.
- Add a unit test that sets `expira_em = now().minusSeconds(1)` and verifies re-upload is triggered.
- The `mensagens_log` INSERT for direction `out` must happen AFTER the media_id is confirmed valid (i.e., after the Meta send returns 200), not before.

**Warning signs:**
- Error 131053 (`HTTP 429` / invalid media) in outbound logs.
- PDF delivery failures clustered ~30 days after a file was first uploaded.
- `out` rows in `mensagens_log` with no corresponding Meta message delivery receipt (`status: delivered`).

**Phase to address:** Phase implementing WHATS-08 + WHATS-11 (document send + media cache). Integration test (QA-02) must cover the cache-expired → re-upload → send path.

---

### Pitfall C-08: Media download URL expires in 5 minutes — async queue delay causes 404

**Severity:** P1 (incoming media from customers — e.g., CPF photo, signed document — permanently lost)

**What goes wrong:**
When a customer sends a document/image via WhatsApp, the webhook payload contains a media `id` (not the bytes). The implementation must call `GET /v21.0/{media-id}` to obtain a time-limited download URL, then download the binary. The download URL is only valid for **5 minutes**. If the webhook is queued for async processing (correct pattern per C-05) but the queue is backed up or the async thread is busy, the download URL is fetched after expiry → 404.

**Why it happens:**
The "ack first, process later" pattern (correct for sync timeout avoidance) conflicts with the 5-minute media URL expiry. The async processing delay can easily exceed 5 minutes under load or during GC pauses.

**How to avoid:**
Split the async work into two ordered steps:
1. After returning 200: immediately (in the async listener, highest priority) fetch the media URL and download the binary to local storage.
2. Only after binary is persisted locally: proceed with ERP callback dispatch, route command, etc.

This ensures the media download always happens within the async listener's first action, keeping the window tight. Add a timeout on the media URL fetch: if the URL returns 404, log a WARN with the wamid and skip — the message is still persisted as received, but media content is unavailable.

**Warning signs:**
- HTTP 404 when calling the media download URL.
- Error log: `Unable to retrieve media for wamid=<id>: 404 Not Found` clustered during high-traffic periods.
- Incoming messages of type `document` or `image` stored in `mensagens_log` with `media_id = null`.

**Phase to address:** Phase implementing WHATS-03 (inbound persistence). Media download for inbound messages is scoped here even if WHATS-08 covers outbound uploads.

---

### Pitfall C-09: Bearer token in Authorization header logged by Spring Boot request/response logging

**Severity:** P1 (accessToken leaks to log files; anyone with log access can impersonate the WABA)

**What goes wrong:**
Spring Boot's `CommonsRequestLoggingFilter`, Spring Security DEBUG logs, or a custom `ExchangeFilterFunction` that logs request/response details for the `WhatsAppCloudClient` WebClient/RestClient will log the full `Authorization: Bearer <accessToken>` header. The System User token is effectively a permanent secret — if leaked in logs, it grants full WABA access.

**Why it happens:**
Developers add request logging during debugging and forget to scope it to non-sensitive headers. The accessToken is stored in `WhatsAppProperties.accessToken` and injected into every outbound request header.

**How to avoid:**
- Configure the outbound RestClient with a custom `ClientHttpRequestInterceptor` or `ExchangeFilterFunction` that masks the `Authorization` header in logs: replace the token value with `Bearer [REDACTED]`.
- Set log level for `org.springframework.web.client` to `INFO`, not `DEBUG`, in production.
- Ensure `WhatsAppProperties.accessToken` is never serialized in actuator endpoints (`/actuator/env`) — use Spring Boot's `@SensitiveEndpoint` or exclude the property with `management.endpoint.env.keys-to-sanitize`.
- Similarly, `appSecret` and `verifyToken` must be excluded from any diagnostic logging.

**Warning signs:**
- `Authorization: Bearer eyJ...` appearing in application logs.
- `/actuator/env` endpoint exposing `app.whatsapp.accessToken` in plaintext.
- Log files in `C:\ERPKit\logs\` readable by non-admin users.

**Phase to address:** Phase implementing WHATS-07 (outbound client) and WHATS-15 (WhatsAppProperties). Security must be addressed at first-use, not post-delivery.

---

### Pitfall C-10: hub.challenge response wrapped in JSON instead of plain text

**Severity:** P1 (webhook verification always fails — can never receive real messages)

**What goes wrong:**
Meta's webhook verification sends `GET /webhook?hub.mode=subscribe&hub.verify_token=X&hub.challenge=Y`. The endpoint must respond with HTTP 200 and the body `Y` — a raw plain-text string, nothing else. Common mistakes: (a) wrapping in JSON `{"challenge": "Y"}`, (b) wrapping in a `ResponseEntity<Object>` that serializes with Jackson to a JSON string (adds quotes: `"Y"`), (c) Spring MVC's `produces = application/json` media type being applied globally.

**Why it happens:**
Spring Boot's `@RestController` convention automatically serializes return values to JSON. A controller method returning `String` from a `@GetMapping` that produces JSON will return `"Y"` (with quotes) instead of `Y`.

**How to avoid:**
The verification endpoint must explicitly produce `text/plain`:

```java
@GetMapping(value = "/webhook/whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
public ResponseEntity<String> verificarWebhook(
    @RequestParam("hub.mode") String mode,
    @RequestParam("hub.verify_token") String verifyToken,
    @RequestParam("hub.challenge") String challenge) {

    if ("subscribe".equals(mode) && properties.getVerifyToken().equals(verifyToken)) {
        return ResponseEntity.ok(challenge);
    }
    return ResponseEntity.status(403).build();
}
```

Note: Spring MVC handles `hub.mode` as a request parameter name containing a dot — use `@RequestParam("hub.mode")` with the exact string (not `hub_mode`).

**Warning signs:**
- Meta's dashboard shows "Callback URL validation failed" or "The callback URL or verify token couldn't be validated."
- Curl test: `curl -v "http://localhost:9193/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=test&hub.challenge=abc"` returns `"abc"` (with quotes) instead of `abc`.

**Phase to address:** Phase implementing WHATS-01. First thing to implement and test — nothing else works until this passes.

---

### Pitfall C-11: verifyToken leaked via Spring request logging query parameters

**Severity:** P1 (verifyToken in logs → attacker can forge GET verification requests)

**What goes wrong:**
`hub.verify_token` is sent as a query parameter in the GET verification request. Spring Boot's `CommonsRequestLoggingFilter` (if enabled) and servlet access logs log the full request URI including query string. The verifyToken appears in logs in plain text.

**Why it happens:**
Request logging is often enabled during development and left on in production for debugging. The verifyToken is treated as a configuration value, not a secret.

**How to avoid:**
- Store `verifyToken` in `WhatsAppProperties` and inject from an environment variable (never hardcode).
- If request logging is enabled, configure it to exclude query parameters: `filter.includeQueryString=false` or add a custom `CommonsRequestLoggingFilter` that masks `hub.verify_token`.
- Rotate the verifyToken if it appears in logs. Note: the verifyToken is only used during webhook setup, not for ongoing operations.

**Warning signs:**
- Log line: `GET /webhook/whatsapp?hub.mode=subscribe&hub.verify_token=meu-token-secreto&hub.challenge=...`
- `verifyToken` appearing in `application.yml` without `${...}` environment variable reference.

**Phase to address:** Phase implementing WHATS-01 + WHATS-15. Both must be addressed together.

---

### Pitfall C-12: Missing WABA → App subscription ("Shadow Delivery" — no webhooks received)

**Severity:** P1 (silently receives zero webhooks; entire system appears functional but never fires)

**What goes wrong:**
There are three separate layers in Meta's webhook architecture:
1. App-level webhook URL (configured in Meta for Developers dashboard)
2. WABA subscription to the app (requires explicit `POST /{WABA_ID}/subscribed_apps` via Graph API)
3. Phone number associated with the WABA

Meta's 2025 UI redesign broke the automatic WABA-to-App subscription that older flows established. The app webhook URL passes verification, the test webhook button works, but real messages from users never arrive because the WABA is not subscribed to the app.

**Why it happens:**
This is an undocumented gap in Meta's developer experience. The UI shows the webhook as "Active" but the subscription is missing at the WABA layer.

**How to avoid:**
Add an explicit verification step to the RUNBOOK.md (QA-05):
1. `GET https://graph.facebook.com/v21.0/{WABA_ID}/subscribed_apps` — verify the app appears in the list.
2. If not: `POST https://graph.facebook.com/v21.0/{WABA_ID}/subscribed_apps` with the System User token.
3. Add a health-check endpoint `GET /api/whatsapp/status` (WHATS-17) that calls the Graph API to verify subscription status on startup.

**Warning signs:**
- No webhook events received after deployment despite Meta dashboard showing "Active."
- Test webhook via Meta dashboard succeeds (app-level delivery works) but no real messages appear.
- `GET /{WABA_ID}/subscribed_apps` returns empty array or doesn't include the app.

**Phase to address:** Phase implementing WHATS-01 (setup). Document in RUNBOOK.md. Add startup health-check.

---

### Pitfall C-13: Brazilian phone number 9th-digit normalization — silent delivery failure (error 131026)

**Severity:** P1 (messages silently undeliverable to large portion of Brazilian customer base)

**What goes wrong:**
Brazil's ANATEL added a 9th digit to mobile numbers in 2010. WhatsApp registered numbers BEFORE this change without the 9th digit for most area codes outside São Paulo (11–19), Rio de Janeiro (21, 22, 24), and Espírito Santo (27–28). A customer whose number is `+55 47 9841-78525` may be registered in WhatsApp as `+55 47 841-78525` (13 digits). Sending to the 14-digit form returns error 131026 ("Message Undeliverable") — no useful error message, just "not on WhatsApp."

**Why it happens:**
The ERP stores customer phone numbers exactly as entered by the clerk (often with the 9th digit). The API call uses this stored value directly without normalization.

**How to avoid:**
Implement normalization in `clientes_zap` population. For Brazil numbers (`+55`), apply the rule: if the area code (DDD) is NOT in `{11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 27, 28}` AND the local number has 9 digits (starts with `9`), strip the leading `9` to produce an 8-digit local number.

Also implement bidirectional matching: when a webhook arrives with a phone from a client, match against both the 13-digit and 14-digit forms before declaring "customer not found."

Add this normalization to the RUNBOOK.md so ERPKit knows to normalize numbers when creating `clientes_zap` records.

**Warning signs:**
- Error 131026 in outbound logs for Brazilian numbers with 14 digits total (55 + 2-digit DDD + 9 + 8 digits).
- Customers in states like SC, RS, PR, MG (outside SP/RJ/ES) reporting non-delivery.
- `clientes_zap` phone values with 14 digits for non-SP/RJ/ES area codes.

**Phase to address:** Phase implementing WHATS-05 (client identification) and WHATS-07 (outbound text). Normalization must be applied at INSERT time into `clientes_zap`, not at send time.

---

### Pitfall C-14: media_id URL contains Bearer token — logged or returned in API responses

**Severity:** P1 (accessToken leaked via media URL in logs or client responses)

**What goes wrong:**
The two-step media download process (get URL from `GET /{media-id}`, then download binary with Bearer token in header) means the temporary download URL itself does NOT contain the token — it's a separate header. However, the access token IS sent in the header for the URL-retrieval call. If the URL retrieval response is logged (as part of debug logging of the outbound HTTP client), the URL is logged. If the access token is accidentally included as a query parameter instead of a header (a common copy-paste error from examples), it appears in logs and potentially in browser histories.

**Why it happens:**
Some community examples show the token as a query parameter: `GET {url}?access_token=XXX`. This is an older API pattern. The current correct approach is `Authorization: Bearer XXX` header only.

**How to avoid:**
Always use the `Authorization: Bearer {accessToken}` header for all Meta API calls. Never use `?access_token=` query parameter form. Mask `Authorization` headers in all HTTP client logging as per C-09.

**Warning signs:**
- `access_token=` appearing in any URL in application logs.
- `Authorization` header values in log files.

**Phase to address:** Phase implementing WHATS-08 (media upload/download). Code review checklist item.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Synchronous ERP callback within webhook handler | Simpler code, fewer moving parts | Retry storms under any ERP slowdown; duplicate callbacks | Never — async dispatch costs <50 lines of code |
| Skip idempotency in tests (H2 in-memory, no UNIQUE constraint) | Faster test setup | Masks the ON CONFLICT logic; production UNIQUE violations become silent data corruption | Never — always define UNIQUE on wamid in test schema |
| Hardcode `ultima_mensagem_em` check with `LocalDateTime.now()` in service layer | Simple | Clock drift between JVM and DB causes off-by-second errors near boundary | Acceptable in tests; production must use DB-side `NOW()` in the UPDATE query |
| Store raw phone number from ERP without normalization | No code needed now | Silent 131026 failures for SC/RS/PR/MG customers | Never — normalization is 10 lines |
| Use `ContentCachingRequestWrapper` for HMAC | Spring-native, well-known | Body empty at filter time — HMAC always fails or is skipped | Never — custom wrapper is correct approach |
| Log full request body in webhook filter | Easy debugging | accessToken, verifyToken, customer phone numbers, message content in log files | Only in local dev with sanitized data; never in production |
| Use temporary access token (24h expiry) during development | Fast setup | Integration tests fail every 24h; someone will copy the dev token to production | Acceptable for initial WHATS-01 spike only — generate permanent System User token before QA phase |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Meta webhook verification (GET) | Return JSON `{"challenge": "..."}` or `"challenge"` with quotes | Return raw `String` with `produces = MediaType.TEXT_PLAIN_VALUE`, HTTP 200 |
| Meta HMAC validation (POST) | Use `ContentCachingRequestWrapper.getContentAsByteArray()` in filter | Use custom `HttpServletRequestWrapper` that reads body eagerly at construction |
| Meta HMAC comparison | `computedHex.equals(receivedHex)` string comparison | `MessageDigest.isEqual(computedBytes, receivedBytes)` constant-time |
| Meta media upload (outbound PDF) | Upload on every send, ignoring cache | Check `media_cache` by SHA-256; re-upload only on miss or `expira_em` past |
| Meta media URL (inbound) | Queue async then fetch URL later | Fetch media URL + download binary as FIRST step in async listener (within 5min window) |
| WABA subscription | Trust Meta dashboard showing "Active" | Explicitly verify `GET /{WABA_ID}/subscribed_apps` — missing subscription = silent zero delivery |
| Brazilian phone numbers | Send to 14-digit number as stored in ERP | Normalize: strip 9th digit for DDDs outside 11-19, 21, 22, 24, 27, 28 |
| System User token | Use temporary token from Meta test page | Generate permanent token via Business Settings > System Users; `Never` expiry |
| outbound send failures (5xx) | Retry immediately in same request thread | Resilience4j retry with exponential backoff (already in lib-whatsapp-client pattern) |
| outbound send failures (4xx) | Retry — it'll eventually work | Never retry 4xx except 429 (rate limit). 400/401/403 are permanent failures |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Synchronous ERP callback in webhook handler | P95 > 5s on `/webhook/whatsapp`; Meta retry storms; duplicate wamid inserts | Async dispatch after committing message to DB | First time ERP takes >5s to process a command |
| Media upload on every document send (no cache) | Each PDF send takes 2–4s extra; Meta rate limit 131053 on repeated identical PDFs | `media_cache` by SHA-256 with 30-day TTL | After sending same PDF to 3rd different customer |
| `SELECT wamid FROM mensagens_log WHERE wamid = ?` without index | Idempotency check becomes full table scan as log grows | `UNIQUE` index on `wamid` (migration V2) | After ~10,000 messages in log |
| `SELECT ultima_mensagem_em FROM clientes_zap WHERE telefone = ?` without index | 24h guard check becomes full table scan | Index on `telefone` (migration V1) | After ~500 customers in `clientes_zap` |
| Fetching media URL on inbound (GET /{media-id}) in same thread as webhook processing | Adds 200–500ms to synchronous path; increases risk of 5s timeout | Process media asynchronously, as first step after returning 200 | Any inbound message with media attachment |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| `hub.verify_token` hardcoded in source | Token in version control; forged verifications if leaked | Environment variable `WHATSAPP_VERIFY_TOKEN` injected via `WhatsAppProperties` |
| `accessToken` in application.yml without `${...}` | Permanent WABA credential in version control | Environment variable `WHATSAPP_ACCESS_TOKEN`; fail-fast on boot if blank |
| `appSecret` in application.yml without `${...}` | HMAC can be forged if App Secret is known | Environment variable `WHATSAPP_APP_SECRET`; fail-fast on boot if blank |
| Skip HMAC validation "temporarily" | Any HTTP client can inject arbitrary commands and trigger ERP actions | HMAC validation is non-negotiable from day one; no bypass flag |
| Log full webhook body in PROD | Customer phone numbers, message content, PII exposed in log files | Log only wamid + message type + timestamp; never log message body |
| accessToken as query parameter in Meta API calls | Token appears in URLs, server access logs, proxy logs | Always use `Authorization: Bearer` header; never `?access_token=` |
| `WhatsAppProperties` visible in `/actuator/env` | accessToken + appSecret exposed via management endpoint | Sanitize sensitive keys in actuator config; secure or disable actuator in production |

---

## "Looks Done But Isn't" Checklist

- [ ] **HMAC validation:** Verify it rejects a webhook with a 1-byte-modified body. Test with Portuguese text in body. Test with empty body (must reject, not accept).
- [ ] **Idempotency:** Send same wamid twice — second must return 200 (idempotent) but NOT dispatch a second ERP callback. Verify with concurrent requests.
- [ ] **24h guard:** Test exactly at boundary: `ultima_mensagem_em = now() - 24h + 1s` (allow) vs `now() - 24h - 1s` (reject 409). Verify with fresh DB reads, not mocked timestamps.
- [ ] **hub.challenge:** `curl` the verification endpoint and verify response body is exactly the challenge string with no JSON wrapping, no extra whitespace, no quotes.
- [ ] **WABA subscription:** `GET /{WABA_ID}/subscribed_apps` shows the app in the list. (Without this, zero real messages arrive despite dashboard showing green.)
- [ ] **media_cache expiry path:** Set `expira_em = now() - 1s` in test data. Verify re-upload is triggered. Verify `expira_em` is updated after re-upload.
- [ ] **Bearer token in logs:** Enable DEBUG logging, send a request, grep logs for `Bearer` — must not appear.
- [ ] **verifyToken in logs:** Enable request logging, trigger GET verification, grep logs for the token value — must not appear.
- [ ] **Brazilian phone normalization:** Send to `+5547984178525` (14 digits, DDD 47 = Santa Catarina) — verify normalization strips the 9, resulting in `+554784178525`.
- [ ] **Token permanent:** System User token in WHATS-15 is generated with "Never" expiry, not the 24h temporary token from the Meta test page.
- [ ] **No template API:** Verify `WhatsAppCloudClient` has no `enviarTemplate` method. Code search for "template" in outbound client must return zero results.
- [ ] **WireMock covers error paths:** 5xx from Meta → Resilience4j retry. 4xx (400/403) → no retry, log structured error. `131047` → log + 409 to ERP caller.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| HMAC bypass bug deployed to production | HIGH | Revoke and regenerate App Secret immediately. Audit all webhooks received during exposure window. Redeploy with fix. |
| verifyToken leaked in logs | MEDIUM | Rotate verifyToken in Meta dashboard. Update environment variable. Redeploy. Log rotation to clear exposed files. |
| accessToken leaked in logs | CRITICAL | Revoke token in Meta Business Manager (System Users > Revoke). Generate new token. Update environment variable. Redeploy. |
| Tunnel disconnect + Meta retry burst | LOW | idempotency on wamid absorbs duplicates automatically. Monitor for wamid UNIQUE violations. Check message timestamps to skip stale retries. |
| media_id expired (30 days) | LOW | Delete stale `media_cache` row. Next send triggers fresh upload. No customer impact (PDF is regenerated). |
| WABA subscription missing | LOW | `POST /{WABA_ID}/subscribed_apps`. Messages start flowing within minutes. No data loss (messages not delivered to webhook are not lost — customers can retry). |
| 24h guard race condition allowed paid send | CRITICAL | Audit `mensagens_log` for `out` rows where `criado_em` exceeds `ultima_mensagem_em + 24h`. Contact Meta support if billed incorrectly. Fix the transaction boundary. |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| C-01: 24h window race condition | WHATS-04 + WHATS-13 (webhook persistence + guard) | Unit test: stale timestamp read → guard fires; `ON CONFLICT` → callback not dispatched |
| C-02: HMAC body consumed before filter | WHATS-01 (webhook receiver) | Integration test: modified body returns 403; correct body returns 200 |
| C-03: HMAC timing attack | WHATS-01 (webhook receiver) | Code review: `MessageDigest.isEqual()` only; no `.equals()` on HMAC strings |
| C-04: Unicode charset mismatch in HMAC | WHATS-01 (webhook receiver) | WireMock test with `"Olá, orçamento"` payload; HMAC must pass |
| C-05: Sync processing timeout | WHATS-01 through WHATS-06 | Load test: webhook returns 200 in <500ms even when ERP callback is slow (WireMock 3s delay) |
| C-06: wamid concurrent delivery race | WHATS-02 + WHATS-03 | Concurrent test: 2 identical wamid webhooks → 1 ERP callback dispatched |
| C-07: media_id stale cache (30d) | WHATS-08 + WHATS-11 | Unit test: expired cache entry → re-upload triggered |
| C-08: media download URL 5min expiry | WHATS-03 (inbound persistence) | WireMock: 5min-expired URL → 404 handled gracefully, message persisted without media |
| C-09: Bearer token in logs | WHATS-07 (outbound client) | Log grep test: `Bearer` must not appear in INFO/WARN/ERROR logs |
| C-10: hub.challenge JSON wrapping | WHATS-01 (webhook verification) | Curl test: raw string, no quotes, no JSON |
| C-11: verifyToken in query param logs | WHATS-01 + WHATS-15 | Log grep: verifyToken value must not appear in any log line |
| C-12: Missing WABA subscription | WHATS-01 setup + RUNBOOK.md | RUNBOOK step: verify `subscribed_apps` after setup |
| C-13: Brazilian 9th-digit normalization | WHATS-05 + WHATS-07 | Unit test: `+5547984178525` → normalized to `+554784178525` |
| C-14: media_id URL Bearer token leak | WHATS-08 (media upload/download) | Code review: all Meta API calls use header, not query parameter |

---

## Sources

- Meta for Developers — WhatsApp Business Platform Pricing: https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing
- Meta for Developers — Webhooks setup: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/set-up-webhooks/
- Meta for Developers — Messages webhook reference: https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/reference/messages
- Meta for Developers — Interactive reply buttons: https://developers.facebook.com/documentation/business-messaging/whatsapp/messages/interactive-reply-buttons-messages/
- Meta for Developers — Interactive list messages: https://developers.facebook.com/docs/whatsapp/cloud-api/messages/interactive-list-messages/
- Meta for Developers — Media reference: https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media/
- Meta for Developers — Error codes: https://developers.facebook.com/documentation/business-messaging/whatsapp/support/error-codes
- Meta for Developers — Messaging limits: https://developers.facebook.com/docs/whatsapp/messaging-limits/
- Meta for Developers — Authorization tokens blog: https://developers.facebook.com/blog/post/2022/12/05/auth-tokens/
- Spring Framework — ContentCachingRequestWrapper Javadoc: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/ContentCachingRequestWrapper.html
- Spring GitHub — ContentCachingRequestWrapper issue #28391: https://github.com/spring-projects/spring-framework/issues/28391
- Baeldung — Reading HttpServletRequest Multiple Times: https://www.baeldung.com/spring-reading-httpservletrequest-multiple-times
- Hookdeck — Guide to WhatsApp Webhooks Features and Best Practices: https://hookdeck.com/webhooks/platforms/guide-to-whatsapp-webhooks-features-and-best-practices
- Siri Prasad / Medium — The "Shadow Delivery" Mystery: https://medium.com/@siri.prasad/the-shadow-delivery-mystery-why-your-whatsapp-cloud-api-webhooks-silently-fail-and-how-to-fix-2c7383fec59f
- Chatarmin — WhatsApp Webhooks Setup Security Scaling 2026: https://chatarmin.com/en/blog/whatsapp-webhooks
- Heltar — All Meta WhatsApp Cloud API Error Codes 2025: https://www.heltar.com/blogs/all-meta-error-codes-explained-along-with-complete-troubleshooting-guide-2025-cm69x5e0k000710xtwup66500
- Zoko — Brazilian phone number 9th digit inconsistency: https://www.zoko.io/learning-article/whatsapp-id-brazil-mexico
- Gupshup — Brazilian/Mexican number inconsistencies: https://support.gupshup.io/hc/en-us/articles/4407840924953-A-brief-note-on-the-inconsistencies-for-mobile-numbers-and-their-WhatsApp-IDs-in-Brazil-digit-9-Mexico-digit-1
- Fyno — WhatsApp Rate Limits for Developers: https://www.fyno.io/blog/whatsapp-rate-limits-for-developers-a-guide-to-smooth-sailing-clycvmek2006zuj1oof8uiktv
- Chat2Desk — WhatsApp Business API Billing changes July 2025: https://chat2desk.com/en/blog/articles/whatsapp-business-api-billing-to-change
- YCloud — WhatsApp API Pricing Update July 2025: https://www.ycloud.com/blog/whatsapp-api-pricing-update
- Chatwoot — GitHub issue #13540 (media rate limiting): https://github.com/chatwoot/chatwoot/issues/13540

---

*Pitfalls research for: WhatsApp Cloud API integration (api-whatsapp + lib-whatsapp-client)*
*Researched: 2026-05-05*
