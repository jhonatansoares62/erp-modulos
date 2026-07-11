# Checklist — Captura de dados descartados (observabilidade)

> Implementação direta (sem GSD) das melhorias da **§12** de `ANALISE-OBSERVABILIDADE-CUSTO.md`.
> Objetivo: **parar de descartar** dados úteis (status de entrega, timestamp Meta, conversa/categoria/billable, intenção) e gravar no `mensagens_log` para relatórios + integração uniforme em todos os ERPs.
> Modelo de deploy: **1 banco por cliente**, sem runtime compartilhado → migrations por-install, sem multi-tenant.
> Regra: **custo-zero preservado** (nada aqui envia mensagem) · suíte de testes **sempre verde** (H2 PostgreSQL-mode, Flyway roda nos testes).

Legenda: `[ ]` pendente · `[~]` em andamento · `[x]` feito

---

## Modelo de dados — colunas novas em `whatsapp.mensagens_log` (V7)

| Coluna | Tipo | Preenchida | Item §12 |
|---|---|---|---|
| `id_cliente_erp` | BIGINT | inbound (quando resolve) | #2 |
| `evento_em` | TIMESTAMP | in (msg ts) / out (envio) | #3 |
| `status` | VARCHAR(12) | out (status webhook) | #1 |
| `status_em` | TIMESTAMP | out (status webhook) | #1 |
| `erro_codigo` | VARCHAR(20) | out (status failed) | #1 |
| `erro_titulo` | VARCHAR(255) | out (status failed) | #1 |
| `conversation_id` | VARCHAR(80) | out (status webhook) | #4 |
| `categoria` | VARCHAR(30) | out (status pricing) | #4 |
| `billable` | BOOLEAN | out (status pricing) | #4 |
| `conversa_origem` | VARCHAR(30) | out (status conversation.origin) | #5 |
| `comando` | VARCHAR(255) | inbound (intenção extraída) | #6 |

Índices: `(direcao, criado_em)`, `(id_cliente_erp)`.

---

## Fatias (cada uma = 1 commit, testes verdes)

### Fatia 1 — V7 migration + entity (aditivo puro, sem comportamento) ✅
- [x] `V7__observabilidade_mensagens_log.sql` (11 colunas + 2 índices)
- [x] `MensagemLog.java`: campos + getters/setters (novos nullable)
- [x] `FlywayMigrationTest` + suíte verde (V7 aplicada, Hibernate validate OK, suíte verde)
- [x] commit

### Fatia 2 — Captura do status webhook (para de descartar) → #1, #4, #5 ✅
- [x] `StatusDTO`: liga `conversation{ id, origin.type }`, `pricing{ billable, category }`, `errors[]{ code, title }` (nested statics)
- [x] `StatusEntranteDTO`: carrega os novos campos (record 10 fields)
- [x] `WebhookPayloadParser.extrairStatus`: mapeia + `parseTimestamp` (Unix→Instant)
- [x] `StatusEntregaService.registrar`: UPDATE por `wamid` (REQUIRES_NEW, rank null-safe sent<delivered<read, failed terminal; wamid inexistente = skip)
- [x] `MensagemService.processarWebhook`: loop de descarte → `registrar` (defensivo, não derruba ack)
- [x] testes: parser (fixtures sent+pricing e failed) + `StatusEntregaServiceTest` (aplica/skip/rank/failed) + `MensagemServiceTest` atualizado → **suíte verde**
- [x] commit

### Fatia 3a — `id_cliente_erp` + `comando` na linha de entrada → #2, #6 ✅
- [x] `MensagemLogService.enriquecerEntrada(wamid, idClienteErp, comando, eventoEm)` (find+set+save, REQUIRES_NEW, COALESCE manual, wamid inexistente = no-op) — mesmo padrão do `StatusEntregaService`
- [x] `MensagemAsyncListener`: injeta + chama após extrair comando (roda mesmo sem comando; defensivo)
- [x] testes: `MensagemLogServiceTest` (enriquece/null-não-apaga/skip) + `MensagemAsyncListenerTest` (verify enriquecerEntrada, inclusive sem comando) → **suíte verde**
- [x] commit

### Fatia 3b — Timestamp real da Meta (`evento_em`) → #3 ✅
- [x] threading via `MensagemEntranteDTO` + `MensagemPersistidaEvent` (novo campo `timestamp`)
- [x] `WebhookPayloadParser.extrairMensagem`: `parseTimestamp(msg.getTimestamp())`
- [x] `MensagemService`: passa `m.timestamp()` no event
- [x] `MensagemAsyncListener`: passa `event.timestamp()` no `enriquecerEntrada`
- [x] inbound = ts Meta; outbound segue com `criado_em`/`status_em` (sem churn no WhatsAppCloudClient crítico)
- [x] testes atualizados (records) + suíte verde
- [x] commit

---

### Fatia 5 — Endpoint de relatório `resumo` (expõe o que salvamos, uniforme p/ ERPs) 🚧
- [x] Repo: `contarPorDirecao/Tipo/Status/Categoria` (GROUP BY em [de,ate]) + `contarFaturaveis` (padrão `api-email`)
- [x] `RelatorioUsoService.resumo(de, ate)` → `ResumoUsoResponse` (sem PII; null→rótulos)
- [x] `RelatorioController` `GET /api/whatsapp/relatorios/resumo?de&ate` (ISO, default 30d; ApiKey protege `/api/whatsapp/*`)
- [x] `RelatorioUsoServiceIntegrationTest` (H2 seed: direção/tipo/status/categoria/faturáveis + range) → **suíte verde**
- [x] commit (módulo)
- [x] `lib-whatsapp-client`: `WhatsAppClient.relatorioResumo(de, ate)` + `ResumoUsoResponse` (DTO, datas ISO String) + `WhatsAppClientImplWireMockTest` → verde
- [x] `mvn install` do lib-whatsapp-client (SNAPSHOT no .m2)
- [x] proxy no ERP-ODONTO (`ModulosController` → `GET /api/modulos/whatsapp/relatorios/resumo`) — compila
- [x] tela "Uso (últimos 30 dias)" em Configurações → Módulos → WhatsApp (enviadas/recebidas/faturáveis + chips por status/tipo) — frontend build verde
- [x] **deploy feito e verificado ao vivo:** `api-whatsapp.jar` (V7) no `ERP-Odonto-WhatsApp` (Flyway V7 aplicado, 11 colunas novas, `/relatorios` responde 200 com dados reais: total 48) + jar swap do ERP-Odonto (tela). Proxy do ERP exige login (401 sem sessão, correto).
  - Nota: linhas históricas (pré-V7) saem como `status=pendente` / `categoria=sem_categoria` / `faturaveis=0` — só mensagens NOVAS populam status/categoria/billable/evento_em.

---

## Adiado (precisa de mudança de contrato com o ERP — fora desta rodada)
- [ ] **#6 (resultado):** `casou`/`resultado` (respondido/silêncio/erro) — exige o ERP devolver o desfecho no callback (`ErpCallbackClient.despachar` hoje descarta o corpo) **ou** inferir do *out* seguinte.
- [ ] **#7 (correlação):** `responde_a_wamid` no *out* → tempo de resposta (exige o ERP propagar o wamid de origem).
- [ ] **#8:** `phone_number_id` (multi-número futuro).
- [ ] Endpoints de relatório (`/api/whatsapp/relatorios/*`) + exposição no `lib-whatsapp-client` + tela ERP (fase seguinte).
- [x] **Custo via Meta `pricing_analytics` (§11) — FEITO e verificado ao vivo.** Token tem escopo `whatsapp_business_management`; sintaxe exige `metric_types([...])`+`dimensions([...])`. `WhatsAppProperties.wabaId` (`WHATSAPP_WABA_ID`), `MetaAnalyticsClient`, `GET /api/whatsapp/relatorios/custo`, lib `relatorioCusto`, proxy ERP + card "Custo (Meta)". Deployado: `/custo` responde real (27 msgs, tudo FREE_CUSTOMER_SERVICE, custo 0 — pré-out/2026). Instalador tb costurado: template + `.iss` (`ReadDevConfig` + preservação `SavedWhatsAppWabaId`) + `installer-dev.properties` → fresh installs levam o WABA id (ISCC compila). **Completo.**
