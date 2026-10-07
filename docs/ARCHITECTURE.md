# ARCHITECTURE.md

## Visão geral

```
Cloudflare (DNS, CDN, WAF, Turnstile)
   ├── apps/web  Next.js  (área pública + painel + PWA de check-in)
   └── apps/api  Spring Boot  (monólito modular)
          ├── PostgreSQL        (fonte da verdade)
          ├── Cloudflare R2     (flyers e imagens)
          └── Externos: Mercado Pago · E-mail · Sentry
```

Princípio: **começar simples**. Sem microserviços, sem Redis, sem fila externa no MVP. Cada peça nova exige um ADR justificando.

## Módulos do backend

| Módulo | Responsabilidade |
|---|---|
| `identity` | Usuários, login, sessões, link mágico |
| `organization` | Organizações, membros, papéis, conta de recebimento |
| `event` | Eventos, mídia, lineup, publicação |
| `ticketing` | Tipos, lotes, estoque, ingressos emitidos, token de QR |
| `order` | Carrinho, pedido, reserva, expiração, limite por CPF |
| `payment` | Integração com gateway, webhook, split, estorno |
| `promoter` | Promoters, links, atribuição, comissões |
| `checkin` | Portarias, validação, sincronização offline |
| `finance` | Lançamentos, taxas, repasses |
| `notification` | E-mails e, no futuro, WhatsApp |
| `compliance` | Termos, consentimentos, audit log |

Regras:
- Cada módulo expõe um pacote `api` (interfaces e DTOs públicos). O resto é interno.
- Um módulo **não** acessa repositórios nem tabelas de outro.
- Efeitos colaterais entre módulos usam **eventos de domínio** gravados no outbox.

## Multi-tenant

Banco compartilhado. Toda tabela de dado da organização tem `organization_id`.
O contexto de organização vem do usuário autenticado + vínculo em `organization_members` + dono do recurso. Ver SECURITY.md.

## Máquinas de estado

**Pedido**
```
PENDING_PAYMENT → PAID | EXPIRED | FAILED
PAID            → REFUNDED | PARTIALLY_REFUNDED | CHARGEBACK
```

**Ingresso** (só existe após pagamento)
```
VALID      → CHECKED_IN | TRANSFERRED (gera novo VALID) | CANCELLED
CHECKED_IN → VALID   (somente desfazer check-in, com audit log)
```

**Evento**
```
DRAFT → PUBLISHED → ENDED
DRAFT | PUBLISHED → CANCELLED
```

Toda transição é feita por método de domínio que valida o estado de origem. Transição inválida = exceção.

## Fluxos críticos

### Estoque (sem overselling)
```sql
UPDATE ticket_batches
   SET reserved = reserved + :qty
 WHERE id = :batch_id
   AND status = 'ON_SALE'
   AND sold + reserved + :qty <= capacity;
-- 0 linhas = indisponível
```
- Criação do pedido e reserva na **mesma transação**.
- Pagamento confirmado: `reserved -= qty; sold += qty`.
- Expiração (job a cada minuto): `reserved -= qty`, pedido → `EXPIRED`.
- Virada de lote avaliada após cada venda/expiração e por job de data.

### Pagamento
```
1. POST /orders              → pedido PENDING_PAYMENT + reserva
2. POST /orders/{id}/payment → cobrança no gateway (idempotency key = order id, split)
3. Comprador paga
4. POST /webhooks/mercadopago
     a. valida assinatura
     b. consulta o pagamento na API do gateway (não confia no payload)
     c. upsert em payments por gateway_payment_id (idempotente)
     d. se aprovado: order.markPaid() → evento OrderPaid no outbox
5. Worker do outbox: emite ingressos, envia e-mail, atualiza promoter
6. Web acompanha GET /orders/{id}/status (polling)
```
Pagamento aprovado com pedido `EXPIRED`: tenta reservar de novo; se não houver estoque, estorna e notifica.

### Check-in offline
```
Antes:   operador abre o PWA → baixa lista da portaria (hash do token, nome, tipo, status)
Durante: leitura valida no IndexedDB → grava check-in local → fila
         online: envia em lote POST /checkins/sync
Conflito: vale o check-in com horário mais antigo; duplicados vão para relatório
```

### Outbox
- Tabela `outbox_events` gravada na mesma transação da mudança de estado.
- Worker lê eventos pendentes (`FOR UPDATE SKIP LOCKED`), processa e marca como enviado.
- Consumidores são idempotentes (o mesmo evento pode ser processado duas vezes).

## URLs

| Recurso | URL |
|---|---|
| Evento | `/e/{slug}` |
| Link de promoter | `/e/{slug}?p={code}` |
| Checkout | `/e/{slug}/checkout` |
| Ingresso | `/t/{token}` |
| Organização | `/o/{slug}` |
| Painel | `/painel/...` |
| Check-in | `/checkin/{eventId}` |

## Ambientes

| Ambiente | Uso |
|---|---|
| local | docker compose + Mercado Pago sandbox |
| staging | deploy automático da `main`, sandbox |
| production | deploy por tag, credenciais reais |

## Decisões (ADRs)

Formato: contexto → decisão → consequências. Registrar aqui em ordem.

- **ADR-000 — Monólito modular.** Equipe pequena; microserviços adicionariam operação sem benefício. Extração futura possível graças às fronteiras de módulo.
- **ADR-001 — Autenticação: sessão no servidor em cookie.** *Aceito em 2026-10-07.*
  - **Contexto:** web (Next.js) e API (Spring) ficam no mesmo domínio registrável (`festa.com` / `api.festa.com`). Precisamos de logout imediato, revogar sessão de membro removido da organização e não expor token a JavaScript. A arquitetura evita Redis no MVP.
  - **Decisão:** Spring Security com sessão no servidor guardada no Postgres (**Spring Session JDBC**). Cookie de sessão `HttpOnly; Secure; SameSite=Lax` no domínio pai (`.festa.com`). CSRF ativo nos métodos de escrita com `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN` lido pela web e devolvido no header `X-XSRF-TOKEN`). Webhooks ficam fora do CSRF porque são validados por assinatura. Login com senha, Google (OIDC) e link mágico terminam todos criando a mesma sessão. Sem JWT.
  - **Consequências:** logout e revogação valem na hora (basta apagar a sessão). Nada de token em `localStorage`, o que reduz o estrago de um XSS. Web e API precisam continuar no mesmo site; em dev, `localhost:3000` e `localhost:8080` já contam como mesmo site. Server Components do Next que chamam a API repassam o cookie do usuário. O M1 cria a migration com as tabelas do Spring Session. O tempo de inatividade é configurável, e a sessão longa do operador de check-in é tratada no M8.
- **ADR-002 — Acesso a dados: JPA + SQL nativo.** *Aceito em 2026-10-07.*
  - **Contexto:** a maior parte do domínio é CRUD de agregados com regras e máquinas de estado. As partes críticas (reserva de estoque, relatórios, outbox com `SKIP LOCKED`) precisam de SQL exato e previsível.
  - **Decisão:** **Spring Data JPA** (Hibernate) para agregados e CRUD, com entidades sem setters públicos de status. **SQL nativo via `JdbcClient`** para o `UPDATE` condicional de estoque, relatórios/dashboards e o worker do outbox. Sem jOOQ por enquanto. Flyway é dono do esquema; o Hibernate só valida (`ddl-auto: validate`) e `open-in-view` fica desligado. IDs UUIDv7 gerados na aplicação. Dinheiro mapeado como `long` em centavos.
  - **Consequências:** uma ferramenta só para o caso comum e SQL explícito onde há dinheiro e concorrência. SQL nativo não é verificado em compilação, então toda consulta nativa tem teste de integração com Testcontainers. Repositórios continuam internos ao módulo (`infra`). Se relatórios crescerem muito, reavaliar jOOQ em novo ADR.
- **ADR-003 — Gateway e split.** *Pendente.* Mercado Pago com OAuth do produtor; plataforma não custodia dinheiro.
- **ADR-004 — Comprador ganha conta no primeiro acesso por link mágico.** *Aceito em 2026-10-07.*
  - **Contexto:** o comprador não precisa de cadastro para comprar e acessa "Meus ingressos" por link mágico (SECURITY.md). Era preciso decidir se o link cria uma conta ou abre uma sessão separada "só por e-mail".
  - **Decisão:** o primeiro clique num link mágico cria um usuário sem nome e sem senha, com e-mail já verificado; os seguintes entram na mesma conta. Há um único tipo de sessão (ADR-001) e pedidos se ligam ao comprador por `orders.buyer_user_id`. O link é um token aleatório de 32 bytes no **fragmento** da URL (`/link-magico#token=...`, não vai para logs nem `Referer`), guardado só como SHA-256, vale 15 min e é consumido por um `UPDATE ... RETURNING` atômico. O pedido de link sempre responde 202 e há limite de 5 links por e-mail por hora. Se o link confirmar um e-mail de conta **não verificada** que tinha senha, a senha é descartada e as sessões daquela conta são encerradas (proteção contra conta pré-criada por terceiro).
  - **Consequências:** `users.name` passa a ser opcional. A tabela `users` cresce com compradores, e os termos de uso precisam dizer que a conta é criada no primeiro acesso. Comprador pode depois definir senha ou virar produtor sem migração de dados. Falta limite por IP (Bucket4j, checklist do SECURITY.md) e limpeza periódica de links antigos.
