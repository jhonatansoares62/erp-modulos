# contabil-app — deploy (on-premise)

## Arquitetura de serving
O app do contador **não tem servidor próprio**: é buildado como estático e **servido pelo
próprio jar da api-contabil** (`WebConfig` → `classpath:/static/browser/`), na **mesma
origem e porta** do módulo. Sem porta nova, sem serviço extra, sem CORS. Como a api-contabil
é o mesmo artefato p/ Odonto/Calhas/Mudas, o app viaja junto (reuso de artefato).

- **URL no cliente (Odonto instalado): `http://localhost:8751/`** (porta do módulo instalado —
  a mesma que o ERP usa em `MODULO_CONTABIL_URL`). Em dev: `http://localhost:8750/`.
- Sem conflito de porta: ERP (8090/8110), Postgres (55xx), email/storage/consultas, e o
  contabil (8751) — o app usa a porta do próprio módulo.
- Base URL da API: **relativa** (`API_BASE = ''`). Nada de `localhost` hardcoded. Em dev o
  `ng serve` (4750) faz proxy de `/v1` e `/health` p/ 8750 (`proxy.conf.json`).

## Auth em produção (sem defaults de dev)
Tudo por env/config no serviço WinSW (`installer/service-config-contabil.xml`):
- `CONTABIL_JWT_SECRET` — secret do JWT (≥32 chars). No instalador reusa o `{JWT_SECRET}` gerado.
- `CONTABIL_SEED_EMAIL` / `CONTABIL_SEED_PASSWORD` / `CONTABIL_SEED_NOME` — o **contador seed** é
  criado no 1º boot pelo `ContadorSeeder` (senha gravada em BCrypt). Em produção, o instalador
  deve **gerar e injetar `CONTABIL_SEED_PASSWORD`** (como faz com outros secrets) e exibir ao
  cliente. Sem essa env, cai no default de `application.yml` (só p/ dev).
- Troca de senha do contador: **ainda não há endpoint** (gap conhecido) — por ora a senha é a
  do seed. Fica de backlog um `PUT /v1/auth/senha`.

## Validar localmente (sem rodar o Inno)
```bash
# 1. build de produção do app → static do jar da api-contabil
cd /c/projetos/erp-modulos/contabil-app
export PATH="/c/projetos/ERP-ODONTO/frontend/node:$PATH"
npx ng build --configuration production      # sai em ../api-contabil/src/main/resources/static

# 2. empacota o módulo (jar já leva o app dentro) e sobe
cd /c/projetos/ERP-ODONTO && bash scripts/dev-modulos.sh stop contabil
cd /c/projetos/erp-modulos && mvn -pl api-contabil -DskipTests package
cd /c/projetos/ERP-ODONTO && bash scripts/dev-modulos.sh start contabil

# 3. abrir no navegador (build de PRODUÇÃO servido pelo jar, não ng serve)
#    http://localhost:8750/  → login (contador@erpkit.local / senha do seed) → telas
```

## Build/release (instalador)
`scripts/installer-build.sh` já: (1) builda o `contabil-app` em prod (→ static do módulo),
(2) `mvnw package` empacota o jar **com o app embutido**, (3) copia
`api-contabil.jar` p/ `installer/deps/modules/`.

## Passos restantes no Inno (`installer/erp-odonto.iss`) — a completar/testar no ambiente com Inno
Espelhar o módulo `storage` (que já é serviço + banco):
1. **[Files]**: `Source: "deps\winsw\WinSW.exe"; DestName: "erp-contabil.exe"` e
   `Source: "service-config-contabil.xml"; DestName: "erp-contabil.xml"`.
2. **Banco**: criar `db_api_contabil` (dono `erp_odonto`) no provisionamento de DB (como
   `db_api_storage`). O schema `contabil` o Flyway cria (`create-schemas=true`).
3. **Serviço**: instalar/iniciar `ERP-Odonto-Contabil` (e parar no upgrade, junto dos outros
   `StopService`), com dependência do PostgreSQL.
4. **Tokens**: injetar `{PG_PORT}`, `{DB_PASSWORD}`, `{JWT_SECRET}` no `erp-contabil.xml`
   (`ReplaceInFile`, como nos outros), e **gerar/injetar `CONTABIL_SEED_PASSWORD`**.

Feito isso, no cliente o contador acessa **`http://localhost:8751/`**, loga com o seed, e usa
as 10 telas (Relatórios/Razão/Diário/Plano/Roteiros/Pendências/Inventário/Abertura/Fiscal/DAS)
batendo na api-contabil instalada (mesma origem). ERP, eventos e api seguem iguais.
