# BACKEND.md

## Stack

Java 21 · Spring Boot · Spring Web · Spring Security · Spring Data JPA · Flyway · Bean Validation · Bucket4j · Actuator · Sentry · JUnit 5 · Testcontainers · ArchUnit · Maven.

## Estrutura de pacotes

```
apps/api/src/main/java/com/festa/
├── FestaApplication.java
├── shared/                  # erros, segurança, tenant context, outbox, utilitários de dinheiro
├── identity/
├── organization/
├── event/
├── ticketing/
├── order/
├── payment/
├── promoter/
├── checkin/
├── finance/
├── notification/
└── compliance/
```

Dentro de cada módulo:
```
<modulo>/
├── api/          # interface pública do módulo (XxxService, DTOs, eventos de domínio). Único pacote importável por outros módulos
├── web/          # controllers REST, request/response
├── domain/       # entidades, regras, máquinas de estado
├── app/          # casos de uso (orquestração, transações)
└── infra/        # repositórios, clientes externos
```

Regras verificadas com **ArchUnit** no CI:
- Um módulo só importa `outro.api` ou `shared`.
- `domain` não depende de `web` nem de `infra`.
- Controllers não acessam repositórios diretamente.

## Convenções de API

- REST sob `/api/v1`. JSON em `camelCase`. Datas ISO-8601 com fuso. Dinheiro em centavos (`priceCents`).
- Recursos do painel aninhados na organização: `/api/v1/orgs/{orgId}/events/...`.
- Rotas públicas: `/api/v1/public/...`. Webhooks: `/api/v1/webhooks/{provider}`.
- Paginação por cursor: `?limit=20&cursor=...` → `{ items, nextCursor }`.
- Erros em **Problem Details (RFC 9457)**:
  ```json
  { "type": "https://festa.com/errors/batch-sold-out", "title": "Lote esgotado",
    "status": 409, "detail": "O Lote 2 esgotou.", "errors": [] }
  ```
- Códigos: 400 validação · 401 sem sessão · 403 sem papel · 404 não encontrado ou de outra organização · 409 conflito de estado/estoque · 422 regra de negócio · 429 rate limit.
- `Idempotency-Key` aceito em `POST /orders` e `POST /orders/{id}/payment`.

## Endpoints da Fase 1

### Públicos
```
GET  /public/events/{slug}
GET  /public/events/{slug}/availability
POST /public/orders                       # cria pedido + reserva
GET  /public/orders/{id}/status
POST /public/orders/{id}/payment          # PIX ou cartão
GET  /public/tickets/{token}
POST /public/magic-links                  # "meus ingressos"
GET  /public/me/tickets                   # sessão via link mágico
```

### Autenticação
```
POST /auth/signup · POST /auth/login · POST /auth/logout
GET  /auth/google/callback · POST /auth/magic-link/consume · GET /auth/me
```

### Painel
```
POST /orgs                                   · GET /orgs/{orgId}
GET|POST /orgs/{orgId}/members               · POST /orgs/{orgId}/payment-account/connect
GET|POST /orgs/{orgId}/events                · GET|PATCH /orgs/{orgId}/events/{id}
POST /orgs/{orgId}/events/{id}/publish       · POST /orgs/{orgId}/events/{id}/media/upload-url
GET|POST /orgs/{orgId}/events/{id}/ticket-types
GET|POST|PATCH /orgs/{orgId}/events/{id}/batches
GET  /orgs/{orgId}/events/{id}/dashboard
GET  /orgs/{orgId}/events/{id}/attendees
GET|POST /orgs/{orgId}/events/{id}/promoters
GET  /orgs/{orgId}/events/{id}/finance
POST /orgs/{orgId}/orders/{id}/refund        # ADMIN+
```

### Check-in
```
GET  /orgs/{orgId}/events/{id}/checkin/manifest   # lista para modo offline
POST /orgs/{orgId}/events/{id}/checkins           # online
POST /orgs/{orgId}/events/{id}/checkins/sync      # lote offline
POST /orgs/{orgId}/checkins/{id}/undo             # ADMIN+
```

### Webhooks
```
POST /webhooks/mercadopago
```

## Padrões de implementação

- **Transações** na camada `app`. Reserva de estoque e criação de pedido na mesma transação.
- **Entidades** com métodos de domínio (`order.markPaid()`, `ticket.checkIn()`), sem setters públicos de status.
- **Dinheiro:** value object `Money(long cents)`; nunca `double`.
- **Tempo:** injetar `Clock` para testar expiração e virada de lote.
- **Tenant:** `TenantGuard` resolve a organização do recurso e valida o papel do usuário antes de qualquer caso de uso do painel.
- **Outbox:** `OutboxPublisher.publish(event)` dentro da transação; `OutboxWorker` com `@Scheduled` + `FOR UPDATE SKIP LOCKED`.
- **Jobs:** `@Scheduled` + lock no banco (ShedLock) para expiração de pedidos e virada de lotes por data.
- **Clientes externos** atrás de interface (`PaymentGateway`, `EmailSender`, `FileStorage`) com fakes para teste.
- **Configuração** via `application.yml` + variáveis de ambiente; perfis `local`, `staging`, `prod`.
- **Logs** estruturados (JSON) com `requestId`; sem dados pessoais.

## Testes

| Tipo | Ferramenta | Obrigatório para |
|---|---|---|
| Unitário | JUnit 5 | regras de domínio (virada de lote, cota de meia, máquinas de estado, comissão) |
| Integração | Testcontainers (Postgres real) | repositórios, reserva de estoque, webhook, outbox |
| Concorrência | Testcontainers + `ExecutorService` | overselling (PLAN M4) |
| Segurança | MockMvc | isolamento entre organizações em todo endpoint do painel |
| Arquitetura | ArchUnit | fronteiras de módulo |

Cobertura não é meta; **toda regra de dinheiro, estoque e permissão tem teste**.
