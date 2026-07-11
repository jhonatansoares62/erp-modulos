# Arquitetura de Bancos — ERP Kit

Padrão de organização do PostgreSQL para os ERPs (Calhas, Mudas, Odonto) e os
módulos plugáveis (email, storage, whatsapp, contábil, consultas).

## Regra

> **1 instância PostgreSQL por instalação + 1 banco por módulo dentro dela.**

- Módulo **não** tem instância própria — é um **banco** (database) dentro da instância do ERP.
- Fronteira de isolamento = **banco**. Não schema, não instância.
- Módulo `consultas` é stateless (BrasilAPI) → **sem banco**.

---

## Produção (cliente / on-premise `.exe`)

Cada ERP instalado sobe a **sua própria instância** (1 serviço Windows, 1 porta, 1 data dir):

```
Máquina do cliente
└── PostgreSQL  (1 instância)
    ├── db_erp_<erp>       ← banco do ERP (dados de negócio)
    ├── db_api_email       ← módulo e-mail      (schema interno: public)
    ├── db_api_storage     ← módulo storage     (schema interno: public)
    ├── db_api_whatsapp    ← módulo whatsapp    (schema interno: whatsapp)
    └── db_api_contabil    ← módulo contábil    (schema interno: contabil)
```

Nomes de módulo são **genéricos** (`db_api_<módulo>`) porque cada instalação já é
isolada pela própria instância — não há colisão entre ERPs.

### Estado atual instalado (verificado 2026-07-11)

Todas as instalações têm o **mesmo conjunto** de 4 bancos de módulo (uniformizado em 2026-07-11):

| ERP | Porta | Data dir | Bancos |
|-----|:-----:|----------|--------|
| Calhas | 5433 | `Program Files (x86)\ERP Calhas\data\pgdata` | `db_erp_calhas` + `db_api_email`, `db_api_storage`, `db_api_whatsapp`¹, `db_api_contabil`¹ |
| Mudas  | 5434 | `Program Files (x86)\ERP Mudas\data\pgdata`  | `db_erp_mudas` + `db_api_email`, `db_api_storage`, `db_api_whatsapp`¹, `db_api_contabil`¹ |
| Odonto | 5436 | `Program Files (x86)\ERP Odonto\data\pgdata` | `db_erp_odonto` + `db_api_email`, `db_api_storage`, `db_api_whatsapp`, `db_api_contabil` |

Todos os bancos são donos da role do próprio ERP (`erp_calhas`/`erp_mudas`/`erp_odonto`), UTF8/C.

¹ Banco **provisionado mas ainda vazio** — o módulo não está implantado nesse ERP.
O schema/tabelas são criados pelo Flyway quando o JAR do módulo subir apontando pra ele.
(Follow-up: replicar essa provisão no **instalador** pra que installs novos já criem os 4 bancos.)

---

## Desenvolvimento (máquina do dev)

**1 instância compartilhada** (porta `5432`) guarda **todos** os ERPs — por
conveniência, pra não rodar 3 Postgres no dev.

Como todos dividem a mesma instância, os bancos de módulo são **prefixados por ERP**
(`db_<erp>_<módulo>`) pra não colidir:

```
PostgreSQL:5432  (1 instância, todos os ERPs juntos)
├── db_erp_calhas
│   ├── db_calhas_email
│   └── db_calhas_storage
├── db_erp_mudas
│   ├── db_mudas_email
│   ├── db_mudas_storage
│   └── db_mudas_whatsapp
├── db_erp_odonto
│   ├── db_odonto_email
│   ├── db_odonto_storage
│   ├── db_odonto_whatsapp
│   └── db_odonto_contabil
└── db_erp_console
```

> **Estado (2026-07-11):** Odonto e **Calhas** já estão nesse padrão. O Calhas foi
> renomeado `db_api_email`/`db_api_storage` → `db_calhas_email`/`db_calhas_storage`.
> Os módulos dev do Calhas sobem em **portas dedicadas: email 8510, storage 8520**
> (via `ERP-CALHAS/scripts/dev-modulos.sh`, config em `C:\erpkit\config\erpcalhas\modulos\`),
> **separadas dos serviços instalados** (9091/8085 → 5433) pra dev e instalado conviverem.
> O backend dev (8081) aponta pra 8510/8520 via `application-dev.yml`; um clique no **Debug
> do IntelliJ** (run config `ERP Calhas DEV`) sobe os módulos no before-launch + o backend.
>
> **Faixas de porta dev por ERP** (centena = ERP): Calhas **85xx** (email 8510, storage 8520),
> Mudas **86xx** (alvo), Odonto **87xx** (email 8710, storage 8720, whatsapp 8730, contabil 8750).
>
> **Falta o Mudas:** ainda não tem módulos dev próprios — `db_mudas_*` acima é alvo;
> hoje o Mudas dev ainda reusa o módulo instalado via HTTP.

---

## Convenção de nomes

| Contexto | Banco do ERP | Banco do módulo |
|----------|--------------|-----------------|
| **Produção** (instância isolada por ERP) | `db_erp_<erp>` | `db_api_<módulo>` |
| **Dev** (instância compartilhada 5432) | `db_erp_<erp>` | `db_<erp>_<módulo>` |

O nome real do banco vem **sempre da config**, nunca hardcoded no jar:
- **Produção:** env var no serviço WinSW (`installer/service-config-<módulo>.xml`).
- **Dev:** `C:\erpkit\config\<erp>\modulos\<módulo>.properties` (via `--spring.config.additional-location`).

O `application.yml` do módulo **não** deve trazer default acoplado a ERP.
Meta: default `fail-fast` vazio (`${..._DB_URL:}`). Estado: `api-whatsapp` já está
assim; `api-email`/`api-storage` ainda trazem default `db_api_*`/`erp_calhas` — a alinhar.

---

## Por que 1 instância e não 1 por módulo

Camadas de isolamento, da mais pesada à mais leve:

| Nível | O que isola | Custo | Uso |
|-------|-------------|-------|-----|
| Instância por módulo | processos, RAM (shared_buffers), porta, serviço, backup | alto | só p/ versões de PG diferentes ou HA — ❌ exagero on-premise |
| **Banco por módulo** (1 instância) | catálogo separado, sem query cross-database, permissões separadas | baixo | ✅ **padrão adotado** |
| Schema por módulo (1 banco) | namespace de tabelas | mínimo | blast-radius maior, chato de backup/mover — só p/ organizar *dentro* do módulo |

O app do módulo já é standalone na camada de **aplicação** (JAR/porta/serviço próprios);
a camada de **dados** não precisa do mesmo nível — vários apps dividem uma instância,
cada um dono do seu banco. Padrão consagrado.

O bug que originou o isolamento (fila de um ERP aparecendo com dados de outro) era
**banco compartilhado**, não instância compartilhada → resolvido por banco-por-ERP/módulo.

---

## Adicionar um módulo novo

1. `CREATE DATABASE db_api_<módulo>;` na instância do ERP (prod) — ou `db_<erp>_<módulo>` (dev).
2. Apontar a config do módulo pro banco (env no `service-config-<módulo>.xml` / `C:\erpkit`).
3. Subir o JAR como serviço WinSW na porta do módulo.
4. O Flyway do módulo cria schema/tabelas no boot (`baseline-on-migrate`, `create-schemas`).
