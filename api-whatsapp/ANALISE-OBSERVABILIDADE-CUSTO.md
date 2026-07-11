# Análise técnica — Observabilidade + Relatórios de uso e custo (api-whatsapp)

> **Status:** proposta / "como fazer". **Nada implementado.** Alimenta um futuro `/gsd-plan-phase`.
> **Driver:** a partir de **2026-10-01** a Meta passa a cobrar mensagens **utility** e **service** enviadas **dentro** da janela 24h (hoje grátis). O módulo foi desenhado como "reativo puro custo zero" e **nunca mediu custo**. Precisamos medir/projetar antes da data.
>
> ⚠️ **LEIA A §11 PRIMEIRO.** A Graph API da Meta **já expõe custo e volume** (`pricing_analytics`), quebrado por categoria e por *free vs billable*. Isso **muda a estratégia**: custo vem da Meta (autoritativo, reflete a virada de out/2026 nativamente); o `mensagens_log` local fica para o que a Meta não faz (por-paciente, por-conversa, conteúdo). As §§2-4 (carimbar `billable`, tabela de preço, capturar `pricing` do webhook) ficam **parcialmente superadas** para custo — mantidas para relatório de uso local.

---

## 1. Estado atual confirmado no código

Levantamento validado arquivo:linha (correções do briefing em **negrito**).

| Área | Confirmado | Ref |
|---|---|---|
| Tabela de log | `whatsapp.mensagens_log`: `id, wamid (UNIQUE), telefone, direcao (CHECK in/out), tipo VARCHAR(50), conteudo TEXT, media_id, criado_em DEFAULT NOW()`. Índices `telefone`, `criado_em`. | `V2__criar_tabela_mensagens_log.sql:17-30` |
| **Coluna `wa_id`** | **Existe, mas veio na V5** (não estava no briefing como parte da V2). NULL para saídas. | `V5__adicionar_wa_id_mensagens_log.sql`, `MensagemLog.java:46-49` |
| Entity | `tipo` é `String` (flexível p/ tipos novos, D-05); `conteudo` `columnDefinition="TEXT"`; `criadoEm` `insertable=false` (relógio do banco). `toString()` **não** expõe `conteudo` (PII). | `MensagemLog.java:55-70,124-133` |
| Persistência entrante | fast-path `@Transactional`: parse → idempotência (UNIQUE wamid) → `publishEvent` → async. | `MensagemService.java:71-93` |
| Persistência saída | cada envio grava `MensagemLog(wamid, telefone, Direcao.out, "<tipo>", texto/caption, mediaId)`; `tipo ∈ {text, document, interactive_button, interactive_list}`. | `WhatsAppCloudClient.java:126,161,188,225` |
| Repositório | **só** `findByWamid`, `findByTelefoneOrderByCriadoEmDesc`, `findTop200ByOrderByIdDesc`, `count()`. **Zero agregação.** | `MensagemLogRepository.java:26-32` |
| **Status de entrega ignorados** | `sent/delivered/read/failed` são parseados e **descartados** (só `log.debug`) — decisão D-06. | `MensagemService.java:95-99` |
| **Pricing/conversation descartados no parse** | `StatusDTO` tem `@JsonIgnoreProperties(ignoreUnknown=true)` e só liga `id/status/recipient_id/timestamp` → **`pricing` e `conversation` da Meta nunca chegam a ser lidos**. `StatusEntranteDTO` guarda só `wamid/status/telefone`. | `StatusDTO.java:10-24`, `StatusEntranteDTO.java:11-15`, `WebhookPayloadParser.java:161-167` |
| `tipo` ≠ categoria | `tipo` guarda o **formato** Meta (`text`, `interactive_list`), **não** a categoria faturável. | `WebhookPayloadParser.java:110-130` |
| Console dev/meta | `MonitorController @Profile({"dev","meta"})`, path `/monitor`, só feed cru (`findTop200`), **sem agregação**, **não roda em produção**. | `MonitorController.java:33-34,61-76` |
| Conversa | `estado_conversa` = **placeholder** (`telefone + ultima_atualizacao`). Não há conceito de conversa agregada. | `V4__criar_tabela_estado_conversa.sql` |
| Janela 24h | `WindowEnforcementService.verificarJanela` (via `@JanelaProtegida`) roda **antes de todo envio** → **toda saída é garantidamente dentro da janela** (portanto "service" hoje). Âncora = `clientes_zap.ultima_mensagem_em`. | `WindowEnforcementService.java:71-89`, `WhatsAppCloudClient.java:113-116…` |
| Vínculo telefone↔ERP | `clientes_zap`: `id_cliente_erp` (nullable), `telefone` (unique), `ultima_mensagem_em`, `criado_em`. | `ClienteZap.java:36-46` |
| Métricas | **Actuator/Micrometer NÃO estão no classpath.** Bloco `management:` é preparação dormente. | `application.yml:115-127` |
| Segurança | `ApiKeyFilter` (lib-shared `br.com.erpkit.shared.security.ApiKeyFilter`), `/*` exige `X-API-Key`; públicos = `{/webhook, /monitor}` + health. **`/api/whatsapp/*` já exige API key.** | `SecurityConfig.java:62-73` |
| Precedente de agregação | `EmailRepository`: `@Query("SELECT e.status, COUNT(e) FROM Email e GROUP BY e.status") List<Object[]> contarPorStatus()`; `EmailService.estatisticas()` → `Map<String,Long>`; `EmailController @RequestMapping("/api/emails") GET /estatisticas`. | `api-email/.../EmailRepository.java:22-23`, `EmailService.java:130-136`, `EmailController.java:23,67-69` |
| Controller de produção | `WhatsAppController @RequestMapping("/api/whatsapp")`, thin wrapper, `ModuloException`, `GlobalExceptionHandler` (lib-shared). | `WhatsAppController.java:47-49` |
| Client lib | `lib-whatsapp-client`: `WhatsAppClient` (interface) + `WhatsAppClientImpl` (HTTP) + DTOs; precedente de proxy de config = `MetaConfigRequest/Response`. ERP proxia via `ModulosController` (`obterConfig`/`diagnostico`). | `lib-whatsapp-client/.../WhatsAppClient.java`, `WhatsAppClientImpl.java` |
| Migrations | V1..V6 (V6 = `config_meta`). **Próxima = V7.** `ddl-auto: validate` → schema 100% Flyway; entity tem que casar. | `db/migration/*`, `application.yml:54-73` |

**Insight central:** os 5 gaps não são independentes. O **webhook de status da Meta** — hoje 100% descartado — carrega `pricing.category` + `pricing.billable` + `pricing.pricing_model` + `conversation.id` + `conversation.origin.type`. **Capturá-lo resolve de uma vez** a categoria faturável (gap 2), o status de entrega (gap 3) e o agrupamento por conversa (gap 4), de forma **autoritativa (a própria Meta diz)**.

---

## 2. Modelo de dados proposto

**Decisão principal:** estender `mensagens_log` (colunas nullable), **sem** tabela separada de status. Motivos: (a) o relatório precisa do **último** status por mensagem (`taxa de entrega/leitura`), não do histórico completo de transições → 1 coluna `status` "último-vence" basta; (b) evita JOIN nas agregações; (c) espelha o precedente `api-email` (status na própria linha). Uma tabela de eventos de status (append-only) seria over-engineering para os relatórios pedidos.

**V7 — `ALTER TABLE whatsapp.mensagens_log`:**

| Coluna | Tipo | Semântica | Preenchimento |
|---|---|---|---|
| `categoria` | `VARCHAR(20)` null | `service`/`utility`/`marketing`/`authentication`/`none` (billing category). | Saída: `service` (determinístico hoje). Refinado pela Meta na Fase 2. Entrada/legado: `null`. |
| `billable` | `BOOLEAN` null | mensagem gera custo Meta. | Determinístico pela regra de data (ver §3) ou autoritativo (Meta) na Fase 2. |
| `status` | `VARCHAR(12)` null | último status de entrega (`sent`/`delivered`/`read`/`failed`). | `null` até 1º callback. Só saídas. |
| `status_em` | `TIMESTAMP` null | quando o último status chegou. | idem. |
| `conversation_id` | `VARCHAR(80)` null | id de conversa da Meta (status webhook) → agrupa conversa **sem** tabela extra. | Fase 2. |

**Custo NÃO é coluna.** Guardar `custo_estimado` por linha congela um preço que muda retroativamente. Custo é **derivado em tempo de relatório** = `count(billable) × preço_configurável`. O preço fica em config (reusar `config_meta` singleton ou chaves de config), por moeda/país.

**Índices novos (V7):** `idx_mensagens_log_direcao_criado (direcao, criado_em)` para os cortes período+direção; opcional `(categoria)` se o volume crescer. O `UPDATE ... WHERE wamid=?` do status usa o índice UNIQUE já existente (barato).

**Conversa:** **não** criar tabela `conversa` agora. Derivar de: (a) `conversation_id` da Meta quando disponível; ou (b) *session-izing* por `telefone` + gap de 24h (window function SQL). Materializar `conversa` só vira Fase futura se a agregação ficar lenta.

**Retenção/expurgo:** `conteudo` é PII. Propor job agendado configurável: **anonimizar** (`conteudo = NULL`, mantendo metadados p/ os COUNTs) após *N* dias, e **hard-delete** após *M* dias. Default conservador (ex.: anonimiza em 180d). É on-premise → política do cliente, configurável.

**Compatibilidade da regra "custo-zero":** todas as colunas são nullable e todo o rastreamento é de **leitura/derivação** — nenhum envio novo é introduzido. Quem ficar 100% reativo continua custo-zero; só medimos.

---

## 3. Estratégia de categoria de cobrança

**Verdade determinística (hoje, sem depender da Meta):** o módulo **nunca** envia template (`WhatsAppCloudClient` não expõe `enviarTemplate` — trava de design, `WhatsAppCloudClient.java:43-46,110`) e **todo envio é window-enforced**. Logo:

- **Entrada** (`direcao=in`): sempre **não faturável** (mensagem do usuário). `categoria=null`, `billable=false`.
- **Saída** (`direcao=out`): sempre **`service`** (resposta livre dentro da janela). `billable`:
  - `criado_em < 2026-10-01` → `false` (grátis hoje).
  - `criado_em ≥ 2026-10-01` → `true` (passa a ser cobrada).

Ou seja, **a complexidade das 4 categorias da Meta colapsa em 1 (`service`) para o design atual** — dá pra projetar custo **sem tocar no webhook**, contando saídas × preço, aplicando o corte de data. Isso é a Fase 1 (rápida).

**Refinamento autoritativo (Fase 2):** capturar `pricing.category` + `pricing.billable` + `pricing.pricing_model` do status webhook e gravar em `categoria`/`billable`. Isso cobre casos que a heurística não vê: **falha de entrega não é cobrada**, **entry points gratuitos (CTWA / free-tier)** abrem janela `service` grátis, e a própria classificação da Meta. Estratégia de reconciliação: heurística preenche no INSERT; Meta sobrescreve quando o status chega (autoritativo vence).

**Modelo de preço assumido:** per-message (PMP, pós-2025) — cada saída faturável conta 1. O schema (`categoria`+`billable` por linha) já casa com PMP. *(Confirmar — ver §Perguntas.)* As categorias `marketing/utility/authentication` ficam suportadas no schema mas **só apareceriam se o módulo um dia enviar template** (fora do escopo atual); o modelo já fica pronto.

---

## 4. Captura de status de entrega (sem quebrar fast-path/idempotência)

Hoje: `MensagemService.processarWebhook` é `@Transactional` (parse → persist entrante → publishEvent) e **ignora** os statuses no loop `95-99`.

**Proposta:**
1. **Estender o parse** (`StatusDTO` + `StatusEntranteDTO` + `WebhookPayloadParser.extrairStatus`) para ligar `pricing` (billable, category, pricing_model) e `conversation` (id, origin.type). São campos novos com `@JsonProperty`; `@JsonIgnoreProperties` continua tolerante.
2. **Aplicar por `wamid`** um `UPDATE mensagens_log SET status=?, status_em=?, conversation_id=COALESCE(?,conversation_id), categoria=COALESCE(?,categoria), billable=COALESCE(?,billable) WHERE wamid=?`. Como é UPDATE numa saída **já commitada** (em outra transação, portanto visível), é barato e idempotente.
3. **Defensivo (não-fatal):** `wamid` desconhecido (status antes do INSERT da saída, ou msg que não enviamos) → log-and-skip, **sem** abortar o ack 200 do webhook. Envolver em try/catch por status para um erro não derrubar o batch.
4. **Ordem fora de sequência:** guardar com *rank* monotônico (`sent < delivered < read`; `failed` terminal) para não regredir `read → delivered`.
5. **Onde rodar:** espelhar o padrão async existente — publicar `StatusRecebidoEvent` e um `@Async @TransactionalEventListener(AFTER_COMMIT)` (como o de entrante), **ou** um método `@Transactional(REQUIRES_NEW)` dedicado chamado no loop. Preferência: evento async, para **zero** impacto no fast-path do entrante. A idempotência do entrante (UNIQUE wamid) fica intocada — status não passa pelo gate de idempotência.

---

## 5. Endpoints de relatório (produção, `/api/whatsapp/relatorios`)

Sob `/api/whatsapp/*` (já protegido por `ApiKeyFilter`), **diferente** do `/monitor` (dev/meta). Thin wrappers, `Page<T>`, `ModuloException`, DTOs como `record` (convenção do módulo; note: o *ERP* proíbe records, o *módulo* não).

| Endpoint | Retorno | Uso |
|---|---|---|
| `GET /api/whatsapp/relatorios/resumo?de&ate&idClienteErp&categoria` | `ResumoUsoResponse` | KPIs: enviadas, recebidas, por tipo, por categoria, por status, taxa entrega/leitura/falha, **custo estimado** e **custo projetado pós-out/2026**. |
| `GET /api/whatsapp/relatorios/custo?de&ate` | `CustoResponse` | faturáveis por categoria × preço; medido (Meta) vs projetado (heurística). |
| `GET /api/whatsapp/relatorios/conversas?de&ate` | `Page<ConversaResumo>` | nº conversas, msgs/conversa, tempo de resposta (Fase 3). |
| `GET /api/whatsapp/relatorios/mensagens?de&ate&direcao&categoria&page&size` | `Page<MensagemMetadata>` | listagem **só metadados** (sem `conteudo`). |

**MVP (Fase 1):** começar com **um** `GET /api/whatsapp/relatorios/resumo` retornando DTO agregado (evolução do `estatisticas()` do api-email, porém DTO tipado em vez de `Map`, por causa dos múltiplos cortes). Agregações no repositório via `@Query ... GROUP BY` retornando `List<Object[]>` **ou** projeção JPQL direto no DTO. Filtro por cliente = JOIN `clientes_zap` por `telefone` → `id_cliente_erp`.

**DTOs (lib + api):** `ResumoUsoResponse`, `CustoResponse`, `ConversaResumo`, `MensagemMetadata` (sem PII).

---

## 6. Exposição no ERP

Reusar o padrão de config/diagnóstico já existente:
1. **`lib-whatsapp-client`:** adicionar ao `WhatsAppClient` (interface) métodos `relatorioResumo(RelatorioFiltro)` / `relatorioCusto(...)`; `WhatsAppClientImpl` faz `GET /api/whatsapp/relatorios/...` com `X-API-Key`. Novos DTOs em `client.dto` (espelham `MetaConfigResponse`).
2. **ERP (`ModulosController`):** endpoints proxy `GET /api/modulos/whatsapp/relatorios/...` chamando `whatsapp().relatorioResumo(...)` — mesmo padrão de `diagnosticoWhatsapp`/`obterConfigWhatsapp`, com o mesmo tratamento de `WhatsAppIndisponivelException` (painel não cai se o módulo estiver off).
3. **Frontend:** **Configurações → Módulos → WhatsApp** ganha uma aba/seção **"Uso e custo"** (números + projeção out/2026; gráficos na Fase 3), reaproveitando o painel de diagnóstico já existente.

---

## 7. Privacidade / segurança

- **`conteudo` é PII:** DTOs de relatório **nunca** incluem `conteudo`. Resumo = só contagens; listagem = metadados (`id, direcao, tipo, categoria, status, criado_em`). Filtro por cliente usa `id_cliente_erp`, não telefone cru.
- **Retenção/expurgo** (§2): anonimizar `conteudo` após *N* dias + hard-delete após *M* dias, configurável. Mantém agregados históricos sem PII.
- **ApiKey:** endpoints de relatório herdam o `ApiKeyFilter` (`/api/whatsapp/*` exige `X-API-Key`). **Não** adicionar a `additionalPublicPaths`.
- **Logs:** manter a política atual (sem PII/token/query em log; `toString()` mascarado). Agregações não logam conteúdo.

---

## 8. Performance e migração

- **Linhas históricas** (pré-V7): `categoria/status/billable = NULL`. **Backfill** único: `UPDATE mensagens_log SET categoria='service', billable=(criado_em ≥ '2026-10-01') WHERE direcao='out'` (entrada fica null). `status` histórico é **irrecuperável** (statuses nunca foram capturados) → documentar que "taxa de entrega" só existe a partir da Fase 2.
- **Índices:** tabela é **por-clínica** (volume baixo — milhares a poucos milhões em anos). Índice `(direcao, criado_em)` cobre os cortes; custo de escrita desprezível. `UPDATE` de status usa UNIQUE(`wamid`) → O(log n).
- **Expurgo** mantém a tabela limitada. Agregações são GROUP BY numa tabela single-tenant → OK com os índices; usar sempre range em `criado_em`.

---

## 9. Observabilidade opcional (Actuator/Micrometer)?

**Recomendação: NÃO adicionar agora.** Trade-off:
- **Contra:** on-premise, **não há Prometheus central** para *scrape* (cada cliente é isolado) → métricas Micrometer seriam efêmeras e sem TSDB pra consultar. Adiciona dependência + superfície (`/actuator` precisa ser protegido). Relatórios de **negócio** (custo, volume, entrega) são **dado de domínio** que vive melhor como **agregação no Postgres** (a abordagem `mensagens_log`), não como métrica operacional.
- **A favor (futuro):** se surgir necessidade de **ops** (estado do circuit breaker, health do DB) num painel local, aí sim `spring-boot-starter-actuator` (o bloco `management:` já está preparado, `application.yml:115-127`), atrás da API key, exposição mínima.

Ou seja: **relatórios de uso/custo = agregação SQL** (alinhado ao precedente `api-email`). Actuator fica como Fase opcional só para saúde operacional.

---

## 10. Plano incremental

Prioridade nº 1: **contar mensagens faturáveis e projetar custo ANTES de 2026-10-01**, com o mínimo de risco.

| Fase | Entrega | Risco | Esforço |
|---|---|---|---|
| **F1 — Medir custo já (determinístico)** | V7 (`categoria`,`billable` + índice + backfill outbound→service). `WhatsAppCloudClient` carimba `categoria='service'`/`billable` pela regra de data no INSERT da saída. Repo agregações + service + `GET /api/whatsapp/relatorios/resumo` (DTO). Custo = count×preço configurável, com projeção pré/pós out-2026. Lib + proxy ERP + UI mínima (números). **Sem tocar no webhook.** | **Baixo** (append-only, sem mudança no fast-path) | **M** |
| **F2 — Autoritativo + entrega** | Estender `StatusDTO`/`StatusEntranteDTO`/parser (pricing+conversation). V8 (`status`,`status_em`,`conversation_id`). Listener async `atualizarStatusEntrega` por wamid (defensivo, último-vence com rank). Relatório ganha taxas entrega/leitura/falha, `billable`/`categoria` autoritativos (reconcilia com F1), `conversation_id`. | **Médio** (mexe no caminho do webhook, porém isolado e coberto por testes) | **M–L** |
| **F3 — Conversas + retenção + UI rica** | Agregação de conversas (via `conversation_id` ou session 24h), tempo de resposta. Job de expurgo/anonimização configurável. UI de gráficos + filtros de período + dashboard de custo. | **Baixo–Médio** | **M** |
| **F4 (opcional) — Ops metrics** | Actuator/Micrometer só para saúde operacional local, se houver demanda. | Baixo | S |

**TDD em todas:** migrations testadas em H2 (PostgreSQL mode), agregações com dataset seed, parser de status com payloads reais da Meta (pricing/conversation), UPDATE por wamid idempotente/fora-de-ordem. Manter a suíte (~192+) verde.

---

## 11. Revisão — a Meta JÁ expõe custo e volume (`pricing_analytics`)

Pesquisa na Graph API atual (jul/2026): o nó do **WABA** expõe analytics que **entregam o que as §§2-4 tentavam reconstruir**, de forma autoritativa.

### `pricing_analytics` — o mais relevante
```
GET /<WABA_ID>?fields=pricing_analytics.start(<unix>).end(<unix>).granularity(DAILY|HALF_HOUR|MONTHLY)
   [.metric_types(COST,VOLUME)]
   [.pricing_categories(SERVICE,UTILITY,MARKETING,AUTHENTICATION,MARKETING_LITE,AUTHENTICATION_INTERNATIONAL,REFERRAL_CONVERSION)]
   [.pricing_types(REGULAR,FREE_CUSTOMER_SERVICE,FREE_ENTRY_POINT)]
   [.dimensions(PRICING_CATEGORY,PRICING_TYPE,COUNTRY,PHONE,TIER)]
```
Retorna **`volume`** (contagem) e **`cost`** (cobrança aproximada na moeda do WABA), quebrado por **categoria faturável** e por **tipo `REGULAR` (faturável) vs `FREE_CUSTOMER_SERVICE`/`FREE_ENTRY_POINT` (grátis)**.

**Isso resolve o custo de forma nativa:**
- O split **`FREE_CUSTOMER_SERVICE` → `REGULAR`** é *exatamente* a virada de out/2026 (service in-window deixa de ser grátis) → **a Meta reflete a mudança sozinha**; não precisamos hardcodar a regra de data nem carimbar `billable`.
- Categoria faturável vem pronta → **dispensa** a heurística da §3 e **dispensa capturar `pricing` do status webhook só pra custo**.

### `conversation_analytics` — ativo (NÃO deprecado)
`metric_types(COST,CONVERSATION)`, `conversation_categories`, `conversation_types(FREE_ENTRY_POINT,FREE_TIER,REGULAR)`, `conversation_directions`. Dá nº de conversas + custo (modelo de conversa; `pricing_analytics` é o alinhado a per-message). **Lookback cai de 10 anos → 1 ano** em 2025-12-01.

### `analytics` — volume simples
`sent`/`delivered` por período/phone/país. **Sem** custo, **sem** categoria.

### `template_analytics` — irrelevante hoje
custo/entrega/leitura **por template**; o módulo não usa template.

### O que a Meta **NÃO** faz (por isso `mensagens_log` local continua)
- **Nada por-paciente:** agrega por categoria/tipo/país/phone/tier — **não** por `id_cliente_erp` nem conversa individual. Uso por paciente, msgs/conversa, conteúdo → **só local**.
- **Sem granularidade por-mensagem** (agregado por período). Listagem/correlação com paciente → **só local**.
- **Online-only + config:** exige Graph API (internet), o **`WABA_ID`** — que **hoje NÃO está no config** (`app.modulos.whatsapp.*` só tem `phoneNumberId/accessToken/appSecret/verifyToken/erpCallbackUrl/metaApiBaseUrl`, `application.yml:85-94`) — e token com escopo **`whatsapp_business_management`** (envio usa `whatsapp_business_messaging`). **Ação:** adicionar `WABA_ID` ao config (env + `config_meta`) e **verificar o escopo do token**.
- **BSP:** "COST não é retornado para WABAs sob a linha de crédito de um Solution Partner". On-premise direto (app/número próprios da clínica) → custo disponível; sob BSP → custo vazio (cair no cálculo local).

### Recomendação revisada — **HÍBRIDO**
| Precisa de | Fonte | Por quê |
|---|---|---|
| Custo + projeção out/2026 + categoria + free/billable | **Meta `pricing_analytics`** (proxy módulo→lib→ERP) | Autoritativo, reflete a virada nativamente, **zero schema/carimbo**. |
| Volume por paciente/conversa, msgs/conversa, listagem, conteúdo | **`mensagens_log` local** (§5) | Meta não faz por-paciente; offline. |
| Entrega/leitura/falha de mensagem **livre** (não-template) | **status webhook local** (§4) | `analytics` só dá sent/delivered agregado. |

### Plano revisado (substitui §10)
- **F1 — Custo via Meta (mais barato que o F1 anterior):** `WABA_ID` no config + verificar escopo; `MetaAnalyticsClient` (Resilience4j, como `WhatsAppCloudClient`) chamando `pricing_analytics`; `GET /api/whatsapp/relatorios/custo` que **proxia** a Meta (cache curto, ex. 1h); UI de custo/projeção no ERP. **Sem migration, sem carimbo `billable`.** Risco **baixo**, entrega o driver de out/2026 rápido.
- **F2 — Uso local:** agregações do `mensagens_log` (volume por paciente/tipo/período, listagem sem PII) — o que a Meta não dá. `categoria`/índices **só se** quiser relatório de uso desacoplado da Meta.
- **F3 — Entrega + conversas + retenção + UI rica:** status webhook (entrega/leitura/falha free-form) + conversas + expurgo + gráficos.

**Trade-off da fonte de custo:** Meta = oficial e sem manutenção de preço, mas online-only e WABA-level; local = offline e por-paciente, mas *estimado* (tabela de preço + regra de data). **Melhor dos dois:** custo **oficial** da Meta no dashboard, contagem local como *fallback* e para os cortes por-paciente.

---

## 12. O que **não está sendo salvo** hoje — captura recomendada (relatórios + integração cross-ERP)

Varredura do que o fluxo **computa e descarta** ou **nem lê**. Nada disso introduz envio → **custo-zero preservado**. A maioria são colunas *nullable* em `mensagens_log` (estende a decisão da §2; `status` é *latest-wins*).

| # | Dado hoje NÃO salvo | Situação atual (ref) | Valor p/ relatório | Valor p/ integração (todos os ERPs) | Onde salvar | Prio |
|---|---|---|---|---|---|---|
| 1 | **Status de entrega + motivo de falha** (`sent/delivered/read/failed` + `errors[].code/title`) | parseado e **jogado fora** (`MensagemService.java:95-99`); `StatusDTO` nem lê `errors` | taxa **entregue/lida/falhou**; detectar número inválido, janela fechada, template bloqueado | cada ERP mostra "✓ entregue / ✓✓ lida / ✗ falhou" e sabe se deve reenviar | `mensagens_log`: `status`, `status_em`, `erro_codigo`, `erro_titulo` (UPDATE by wamid) | **P1** |
| 2 | **`id_cliente_erp` na própria mensagem** | resolvido tarde e gravado só em `clientes_zap` (`MensagemAsyncListener.java:125-136`); `mensagens_log` só tem `telefone` (`V2:20`) | volume/custo **por paciente** sem JOIN frágil (e `id` muitas vezes null no JOIN) | correlação msg↔paciente direta e estável para qualquer ERP | `mensagens_log`: `id_cliente_erp` (snapshot no insert / quando resolve) | **P1** |
| 3 | **Timestamp real da Meta** | só `criado_em` (relógio do banco); `StatusDTO.timestamp` e o `timestamp` do inbound **descartados** (`StatusDTO.java:15`, `MensagemService.java:78-80`) | série temporal correta com webhook reprocessado/fora de ordem; base p/ tempo de resposta | relatórios de período consistentes entre ERPs | `mensagens_log`: `evento_em` (Meta timestamp) | **P1** |
| 4 | **`conversation_id` + `categoria` + `billable`** (do status webhook da Meta) | `StatusDTO`/parser **ignoram** `pricing`/`conversation` (`StatusDTO.java:10-24`, `WebhookPayloadParser.java:161-167`) | agrupar **conversa** localmente (sem chamar Meta) + **custo POR PACIENTE/conversa** (o `pricing_analytics` só dá WABA-level, §11) | granularidade que a Meta **não** expõe; cada ERP atribui custo ao paciente | `mensagens_log`: `conversation_id`, `categoria`, `billable` | **P2** |
| 5 | **Origem/direção da conversa** (`user`/`business_initiated`, `free_entry_point`) | descartado (idem #4) | separar janela **grátis (FEP)** vs paga; direção | atribuição de custo/gratuidade uniforme | `mensagens_log`: `conversa_origem` | **P2** |
| 6 | **Intenção do paciente + se casou + resultado** | `comando` é extraído, mandado ao ERP e **descartado** (`MensagemAsyncListener.java:139-152`); o callback **descarta o corpo** da resposta do ERP (`ErpCallbackClient.java:95`) | **quais intenções os pacientes usam, taxa de "não entendi"/silêncio, fluxos mais usados** — exatamente o que melhora o bot | todo ERP ganha analytics de bot **idêntico**, sem reimplementar | linhas *in* de `mensagens_log`: `comando`, `intencao`, `casou` (bool), `resultado` (`respondido`/`silencio`/`erro`) — precisa o ERP devolver o resultado no callback **ou** inferir do *out* seguinte | **P3** |
| 7 | **Correlação entrada↔resposta** | cada row é solta (`WhatsAppCloudClient.java:126,161,188,225`) | **tempo de resposta**, threading de conversa | conversas montadas igual em todos os ERPs | `mensagens_log` (out): `responde_a_wamid` | **P3** |
| 8 | **`phone_number_id` que enviou** | não gravado | multi-número (futuro) | installs com +1 número | `mensagens_log`: `phone_number_id` | P4 |

**Forma no schema:** tudo em `mensagens_log` (colunas nullable) — sem tabela nova; conversa derivada de `conversation_id`. `#1/#4/#5` chegam via **captura do status webhook** (§4, já proposto) — ou seja, **uma mudança (parar de descartar o status) entrega #1, #4 e #5 de uma vez**. `#6` é o de maior valor estratégico (melhora o bot, thread anterior) e o único que pede um retorno do ERP no callback.

**Ângulo cross-ERP (o ponto do pedido):** capturar **tudo isso no módulo** (não em cada ERP) e expor via `lib-whatsapp-client` (DTOs + endpoints de relatório, §5-6). Assim o `api-whatsapp` vira a **fonte única e uniforme de observabilidade do WhatsApp** — Calhas, Mudas e Odonto recebem os mesmos relatórios/integrações de graça, e a lógica não se fragmenta por ERP.

---

## Premissas e perguntas em aberto

1. **Modelo de preço:** assumi **per-message (PMP)** — cada saída faturável = 1 unidade. Confirmar que não é conversation-based (mudaria a unidade de contagem).
2. **Fonte do preço:** config fixa por país/moeda (ex.: `config_meta`/chave) ou tabela de preço editável na tela? (BR service message ~ configurável.)
3. **Retenção default** do `conteudo` (ex.: anonimizar 180d / hard-delete 365d)?
4. **Free entry points (CTWA/free-tier):** modelar o desconto de janelas gratuitas já na F2 (via `pricing.billable` da Meta) ou deixar pra depois?
5. **Escopo do relatório:** confirmo **single-tenant** (só a clínica do cliente; nada cross-cliente, coerente com on-premise).
6. **Categoria no schema:** manter as 4 categorias no `VARCHAR` mesmo o módulo só produzindo `service` hoje (futuro-proof p/ se um dia enviar template)? Recomendo sim.
