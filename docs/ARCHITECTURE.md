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
- **ADR-001 — Autenticação.** *Pendente.* Padrão sugerido: sessão Spring em cookie httpOnly no domínio pai (`.festa.com`), SameSite=Lax, com CSRF.
- **ADR-002 — Acesso a dados.** *Pendente.* Padrão sugerido: Spring Data JPA + SQL nativo para relatórios e estoque.
- **ADR-003 — Gateway e split.** *Pendente.* Mercado Pago com OAuth do produtor; plataforma não custodia dinheiro.
