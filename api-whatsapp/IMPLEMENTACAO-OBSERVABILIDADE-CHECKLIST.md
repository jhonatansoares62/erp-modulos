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

### Fatia 2 — Captura do status webhook (para de descartar) → #1, #4, #5
- [ ] `StatusDTO`: ligar `conversation{ id, origin.type }`, `pricing{ billable, category }`, `errors[]{ code, title }`
- [ ] `StatusEntranteDTO`: carregar os novos campos
- [ ] `WebhookPayloadParser.extrairStatus`: mapear
- [ ] `StatusEntregaService.registrar(StatusEntranteDTO)`: UPDATE por `wamid` (defensivo, latest-wins com rank sent<delivered<read, failed terminal; wamid inexistente = skip)
- [ ] `MensagemService.processarWebhook`: trocar o loop de descarte (linhas 95-99) por `registrar`
- [ ] testes: parser (payload com pricing/conversation/errors) + service (persiste status) + rank/fora-de-ordem
- [ ] commit

### Fatia 3 — Timestamp real da Meta (`evento_em`) → #3
- [ ] `MensagemEntranteDTO` + `MensagemPersistidaEvent` + `IdempotencyService`: threading do `MessageDTO.timestamp`
- [ ] inbound: setar `evento_em` no insert
- [ ] outbound: `WhatsAppCloudClient` seta `evento_em = now` no save
- [ ] testes atualizados (construtores dos records)
- [ ] commit

### Fatia 4 — `id_cliente_erp` + `comando` na linha de entrada → #2, #6
- [ ] `MensagemAsyncListener`: após resolver id + extrair comando, `UPDATE mensagens_log SET id_cliente_erp=?, comando=? WHERE wamid=?`
- [ ] repo: método de enriquecimento por wamid
- [ ] testes do listener
- [ ] commit

---

## Adiado (precisa de mudança de contrato com o ERP — fora desta rodada)
- [ ] **#6 (resultado):** `casou`/`resultado` (respondido/silêncio/erro) — exige o ERP devolver o desfecho no callback (`ErpCallbackClient.despachar` hoje descarta o corpo) **ou** inferir do *out* seguinte.
- [ ] **#7 (correlação):** `responde_a_wamid` no *out* → tempo de resposta (exige o ERP propagar o wamid de origem).
- [ ] **#8:** `phone_number_id` (multi-número futuro).
- [ ] Endpoints de relatório (`/api/whatsapp/relatorios/*`) + exposição no `lib-whatsapp-client` + tela ERP (fase seguinte).
- [ ] Custo via Meta `pricing_analytics` (§11) — `WABA_ID` no config + verificar escopo do token.
