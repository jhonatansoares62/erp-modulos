# Stack Research

**Domain:** WhatsApp Cloud API integration — Spring Boot 3.5.9 / Java 21 reusable module (api-whatsapp + lib-whatsapp-client)
**Researched:** 2026-05-05
**Confidence:** HIGH (core stack), MEDIUM (WireMock exact version), HIGH (Meta API endpoints)

---

## Context

This is a SUBSEQUENT milestone adding two new modules to an existing monorepo. The monorepo stack (Spring Boot 3.5.9, Java 21, Maven, Resilience4j 2.2.0, Flyway 9.22.x, PostgreSQL/HikariCP, H2, JUnit 5/Mockito/AssertJ, SpringDoc OpenAPI 2.8.15, Caffeine) is already validated and is NOT re-researched here. Only net-new dependencies for the WhatsApp modules are documented below.

---

## Recommended Stack

### Core Technologies (net-new for api-whatsapp + lib-whatsapp-client)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Spring `RestClient` | via `spring-boot-starter-web` (Spring 6.1+ / Boot 3.2+) | Outbound calls to Meta Graph API v22 (`/messages`, `/media`) | Modern synchronous fluent HTTP client built into `spring-web`; no extra dependency; replaces deprecated `RestTemplate` (scheduled for removal in Spring 8); `.block()` anti-pattern of `WebClient` avoided; direct fit for the non-reactive servlet stack already used in the monorepo |
| Meta Graph API | v22 (`https://graph.facebook.com/v22.0/{phone-number-id}/messages`) | Sending all 4 message types (text, document, interactive-button, interactive-list) + media upload | Only supported path as of Oct 2025 (on-premise API sunset); single unified endpoint for all message types; versioning prefix `v22.0` is current; service-window messages are free |
| Jackson Databind | `2.19.x` managed by Spring Boot 3.5 BOM (`com.fasterxml.jackson.core:jackson-databind`) | Parsing complex Meta webhook payloads (`button_reply`, `list_reply`, `document`, `interactive`) | Already transitive via `spring-boot-starter-web`; zero extra Maven dependency; `@JsonIgnoreProperties(ignoreUnknown = true)` + `@JsonProperty` snake_case mapping handles Meta's polymorphic payload shapes; no explicit version declaration needed in pom.xml |
| `javax.crypto.Mac` (JDK built-in) | Java 21 JDK (no external jar) | HMAC-SHA256 validation of `X-Hub-Signature-256` webhook header | Zero additional dependency; JDK `Mac.getInstance("HmacSHA256")` + `MessageDigest.isEqual` (constant-time) is the canonical approach; no third-party crypto library needed for a single HMAC-SHA256 use case |
| `java.security.MessageDigest` (JDK built-in) | Java 21 JDK (no external jar) | SHA-256 hashing of file bytes for media cache key (sha256 → media_id) | Zero additional dependency; chunked `update()` + `digest()` pattern handles streaming large PDFs; Spring's `StreamUtils` can help buffer the stream |

### Supporting Libraries (net-new, must be declared in pom.xml)

| Library | Maven Coordinates | Version | Purpose | When to Use |
|---------|------------------|---------|---------|-------------|
| WireMock Spring Boot Integration | `org.wiremock.integrations:wiremock-spring-boot` | `3.8.1` (see rationale below) | Integration tests simulating Meta Cloud API (all 4 send types + webhook + 5xx + timeout) | All `@SpringBootTest` integration tests in `api-whatsapp`; `scope: test` |

**WireMock version rationale:** Latest is `4.2.1` (depends on `wiremock-jetty12:3.13.2` + Jetty 12.1.6). Spring Boot 3.5.9 already bundles Jetty; classpath collision between WireMock's Jetty and the embedded container is a known issue. `3.8.1` (released 2025-02-17) explicitly targets Spring Boot 3.x and avoids the Jetty 12 classpath conflict. If collision testing confirms `4.2.1` works without conflict, upgrade; otherwise stay on `3.8.1`. **Use the `standalone` variant to control transitive deps if needed:** `org.wiremock.integrations:wiremock-spring-boot-standalone`.

### Development Tools (no new tooling beyond monorepo baseline)

| Tool | Purpose | Notes |
|------|---------|-------|
| Maven Wrapper (`mvnw`) | Build + test | Existing; `mvnw verify` runs all tests including WireMock integration tests |
| Flyway (9.22.x via BOM) | Schema migrations for `whatsapp` schema | Existing in monorepo; 4 migration scripts: V1 `clientes_zap`, V2 `mensagens_log`, V3 `media_cache`, V4 `estado_conversa`; `baseline-on-migrate: true` |
| H2 (2.x via BOM) | In-memory DB for unit tests | Existing; replace PostgreSQL in `application-test.yml` as done in `api-email`/`api-storage` |
| SpringDoc OpenAPI 2.8.15 | Swagger UI + `/v3/api-docs` | Existing; declare same as other modules |

---

## Maven pom.xml Additions

```xml
<!-- api-whatsapp/pom.xml — net-new test dependency only -->
<dependencies>
    <!-- Everything else (spring-boot-starter-web, spring-boot-starter-data-jpa,
         flyway-core, postgresql, h2, spring-boot-starter-test, resilience4j,
         springdoc-openapi-starter-webmvc-ui) follows the existing module pattern.
         NO net-new production dependency needed — RestClient is in spring-web,
         HMAC and SHA-256 are in the JDK, Jackson is transitive. -->

    <!-- Integration tests: WireMock simulating Meta Cloud API -->
    <dependency>
        <groupId>org.wiremock.integrations</groupId>
        <artifactId>wiremock-spring-boot</artifactId>
        <version>3.8.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

```xml
<!-- lib-whatsapp-client/pom.xml — no net-new runtime dependencies;
     follows lib-consultas-client pattern exactly.
     RestClient bean for internal calls to api-whatsapp is in spring-web (existing). -->
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Spring `RestClient` | `WebClient` (reactive) | Only if migrating the entire module stack to Spring WebFlux; using `.block()` in a servlet app negates all benefits and adds `spring-webflux` to the classpath unnecessarily |
| Spring `RestClient` | `RestTemplate` | Never — RestTemplate is deprecated (Spring Framework 7.1, formal `@Deprecated`; removed in Spring 8); do not use for new code |
| Spring `RestClient` | `Bindambc/whatsapp-business-java-api` community SDK | Avoid (see "What NOT to Use" below) |
| `javax.crypto.Mac` (JDK) | Bouncy Castle (`bcprov-jdk18on`) | Only if you need additional crypto algorithms beyond HMAC-SHA256 and SHA-256; overkill for a single webhook validation use case |
| `MessageDigest` (JDK) | `commons-codec:DigestUtils.sha256Hex()` | Acceptable convenience wrapper; `commons-codec` is already a transitive Spring Boot dependency so `DigestUtils.sha256Hex(inputStream)` is usable without declaring an explicit Maven dep. However, raw `MessageDigest` keeps the dependency surface minimal and is equally readable |
| DB `UNIQUE(wamid)` for idempotency | Redis idempotency store, Idempotency4j | DB UNIQUE is sufficient for on-premise single-instance deployment; Redis adds infra complexity without benefit at expected message volume; `DataIntegrityViolationException` catch pattern handles races safely |
| `wiremock-spring-boot:3.8.1` | `wiremock-spring-boot:4.2.1` | Upgrade to 4.2.1 if classpath conflict testing shows no Jetty collision with Spring Boot 3.5.9; 4.2.1 is the current release and uses `wiremock-jetty12:3.13.2` |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `Bindambc/whatsapp-business-java-api` (community SDK) | Hosted on JitPack (not Maven Central) — adds unreliable external repo; latest release `v0.6.1` dated August 2024, no 2025 activity; unknown Spring Boot 3.5 compatibility; introduces opaque HTTP client stack that conflicts with Resilience4j integration pattern; for a 4-message-type integration the abstraction costs more than it saves | Plain `RestClient` calls to `graph.facebook.com/v22.0/{phoneNumberId}/messages` with hand-rolled DTOs |
| `RestTemplate` | Spring officially deprecating in 7.1, removal in Spring 8; the monorepo's existing `lib-consultas-client` uses it (legacy — should be migrated in future), but new code in `api-whatsapp` must not continue the pattern | `RestClient` |
| Meta WhatsApp On-Premise API | Sunset October 23, 2025 — no longer functional | Meta Graph API Cloud API (`graph.facebook.com/v22.0/...`) |
| WhatsApp template messages (`sendTemplate()`) | Any template send generates a per-message Meta billing charge; the entire architecture is designed around cost-zero guarantees (D4 key decision) | Service messages only (text/document/interactive within 24h window); do NOT implement template send in `WhatsAppCloudClient` |
| Third-party HMAC libraries (Bouncy Castle, etc.) | Unnecessary for a single HMAC-SHA256 + SHA-256 use case; `javax.crypto.Mac` and `java.security.MessageDigest` are JDK built-ins | JDK `Mac.getInstance("HmacSHA256")` |
| Jackson 3 (`tools.jackson:jackson-databind:3.x`) | Spring Boot 3.5.x uses Jackson 2.x (managed via BOM at `2.19.x`); Jackson 3 is adopted only in Spring Boot 4.0; premature upgrade would break BOM alignment | Jackson 2.x via Spring Boot BOM (no explicit version declaration) |

---

## Stack Patterns by Variant

**HMAC-SHA256 webhook validation:**
- Capture raw `byte[]` body BEFORE Spring deserializes JSON via a `OncePerRequestFilter` with `CachedBodyHttpServletRequest`
- Compute `HmacSHA256(appSecret, rawBody)` → hex string with `sha256=` prefix
- Compare with `MessageDigest.isEqual(expected.getBytes(), computed.getBytes())` (constant-time, prevents timing attacks)
- Reject non-matching requests with HTTP 401 before any business logic executes

**Webhook idempotency (wamid):**
- `mensagens_log.wamid` column declared `UNIQUE NOT NULL` in Flyway migration
- On duplicate POST from Meta (retry): `DataIntegrityViolationException` is caught at service layer → return HTTP 200 immediately (Meta stops retrying on 200)
- No application-level check-before-insert — let DB constraint race-condition-safe

**JSON parsing of Meta webhook payloads:**
- Use `@JsonIgnoreProperties(ignoreUnknown = true)` on all webhook DTOs (Meta adds fields across API versions)
- `interactive.type` discriminator field (`"button_reply"` vs `"list_reply"`) drives routing via `if`/`switch` — no Jackson polymorphism annotation needed given only 2 subtypes
- `@JsonProperty("button_reply")` maps snake_case Meta field names to Java camelCase fields

**SHA-256 media cache:**
- `DigestUtils.sha256Hex(InputStream)` from `commons-codec` (already transitive) or raw `MessageDigest` chunked read — both acceptable
- Cache key stored as VARCHAR(64) `arquivo_hash` PK in `media_cache` table
- TTL enforced by `expira_em` timestamp column; scheduled cleanup job or Flyway-triggered purge

**RestClient configuration for Meta Graph API:**
- Base URL: `https://graph.facebook.com/v22.0`
- Authorization header: `Bearer {accessToken}` from `WhatsAppProperties.accessToken`
- Timeout: 10s connect / 30s read (document uploads may be large; tune via `RestClient.Builder` + `HttpComponentsClientHttpRequestFactory`)
- No Resilience4j wrapper on outbound Meta calls from `api-whatsapp` (Meta is authoritative, 5xx = reject 409 to ERP caller); Resilience4j belongs in `lib-whatsapp-client` for calls from ERP → `api-whatsapp` (same pattern as `lib-consultas-client`)

---

## Version Compatibility

| Component | Version | Compatible With | Notes |
|-----------|---------|-----------------|-------|
| `wiremock-spring-boot` | `3.8.1` | Spring Boot 3.x, JUnit 5 | Bundles Jetty-compatible WireMock; use if Jetty classpath conflict with 4.x |
| `wiremock-spring-boot` | `4.2.1` | Spring Boot 3.x, JUnit 5, Jetty 12 | Latest; uses `wiremock-jetty12:3.13.2`; test for classpath conflict with embedded Tomcat in Spring Boot 3.5 before adopting |
| Jackson 2.19.x | Spring Boot 3.5.0 BOM | `com.fasterxml.jackson.core` group | Do NOT upgrade to Jackson 3 (`tools.jackson`) — Spring Boot 4.0 only |
| `RestClient` | Spring Framework 6.1+ (Boot 3.2+) | Replaces `RestTemplate` | No extra Maven dep; `spring-web` already on classpath via `spring-boot-starter-web` |
| Meta Graph API | v22.0 | Current as of 2025-2026 | On-premise API sunset Oct 23 2025; always pin a version in URL to avoid breaking changes on Meta's next major release |

---

## Meta Cloud API Endpoint Reference

| Operation | Method | URL Pattern |
|-----------|--------|-------------|
| Send text / document / interactive-button / interactive-list | POST | `https://graph.facebook.com/v22.0/{phoneNumberId}/messages` |
| Upload media file | POST | `https://graph.facebook.com/v22.0/{phoneNumberId}/media` (`multipart/form-data`, fields: `messaging_product=whatsapp`, `file=<bytes>`) |
| Webhook verification (hub.challenge) | GET | configured endpoint, responds with `hub.challenge` integer |
| Webhook event delivery | POST | configured endpoint, validates `X-Hub-Signature-256` |

Auth: `Authorization: Bearer {accessToken}` on all Graph API calls.

---

## Sources

- Meta for Developers — WhatsApp Cloud API Message API: https://developers.facebook.com/documentation/business-messaging/whatsapp/reference/whatsapp-business-phone-number/message-api — HIGH confidence
- Meta for Developers — Send Messages guide: https://developers.facebook.com/documentation/business-messaging/whatsapp/messages/send-messages — HIGH confidence
- Meta for Developers — On-Premises API Sunset: https://developers.facebook.com/docs/whatsapp/on-premises/sunset — HIGH confidence (on-prem sunset Oct 2025 confirmed)
- Meta for Developers — Webhooks Getting Started (X-Hub-Signature-256): https://developers.facebook.com/docs/graph-api/webhooks/getting-started — HIGH confidence
- Spring.io blog — The state of HTTP clients in Spring: https://spring.io/blog/2025/09/30/the-state-of-http-clients-in-spring/ — HIGH confidence (RestTemplate deprecation roadmap)
- GitHub — `wiremock/wiremock-spring-boot` releases: https://github.com/wiremock/wiremock-spring-boot/releases — MEDIUM confidence (version 3.8.1 confirmed; 4.2.1 claimed latest but Jetty compat not tested against Boot 3.5.9)
- Maven Central — `org.wiremock.integrations:wiremock-spring-boot`: https://central.sonatype.com/artifact/org.wiremock.integrations/wiremock-spring-boot — MEDIUM confidence
- GitHub — `Bindambc/whatsapp-business-java-api`: https://github.com/Bindambc/whatsapp-business-java-api — HIGH confidence (JitPack-only, v0.6.1 Aug 2024, AVOID confirmed)
- Spring Boot 3.5 Release Notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes — HIGH confidence (Jackson 2.19.x managed in 3.5; commons-codec 1.18 managed)
- Baeldung — SHA-256 Hashing in Java: https://www.baeldung.com/sha-256-hashing-java — MEDIUM confidence (JDK MessageDigest pattern)

---

*Stack research for: api-whatsapp + lib-whatsapp-client (WhatsApp Cloud API module, erp-modulos monorepo)*
*Researched: 2026-05-05*
