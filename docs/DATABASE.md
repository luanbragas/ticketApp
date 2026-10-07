# DATABASE.md

## Convenções

- PostgreSQL 16+. Migrations com **Flyway** em `apps/api/src/main/resources/db/migration` (`V001__descricao.sql`).
- Nunca editar migration aplicada. Correção = nova migration.
- Nomes em `snake_case`, tabelas no plural.
- PK: `id UUID` (gerado na aplicação, UUIDv7 preferido para ordenação).
- Toda tabela: `created_at timestamptz not null default now()`, `updated_at timestamptz not null default now()`.
- Dinheiro: `BIGINT` em centavos, sufixo `_cents`.
- Status: `TEXT` + `CHECK` com valores permitidos (mais fácil de migrar que enum nativo).
- Tabela de organização: `organization_id UUID NOT NULL` + índice.
- Exclusão: soft delete (`deleted_at`) só onde houver necessidade de histórico; dados financeiros nunca são apagados.
- CPF: armazenado criptografado (`cpf_encrypted BYTEA`) + `cpf_hash` (HMAC) para busca e unicidade.

## Esquema da Fase 1

### identity
```sql
users (id, name NULL, email UNIQUE, password_hash NULL, email_verified_at, created_at, updated_at)  -- name/senha nulos: conta criada por link mágico (ADR-004)
user_identities (id, user_id FK, provider, provider_user_id, UNIQUE(provider, provider_user_id))
magic_links (id, email, token_hash UNIQUE, expires_at, used_at, created_at, updated_at)  -- token_hash = SHA-256 do token
```

### organization
```sql
organizations (id, name, slug UNIQUE, logo_url, instagram, whatsapp, created_at, updated_at)
organization_members (id, organization_id FK, user_id FK, role CHECK IN ('OWNER','ADMIN','MANAGER','PROMOTER','CHECKIN_OPERATOR'),
                      UNIQUE(organization_id, user_id))
payment_accounts (id, organization_id FK UNIQUE, provider, provider_account_id,
                  access_token_encrypted, refresh_token_encrypted, expires_at)
```

### event
```sql
events (id, organization_id FK, slug UNIQUE, name, description, category,
        starts_at, ends_at, venue_name, address, city, lat, lng,
        min_age SMALLINT, has_open_bar BOOLEAN,
        half_price_quota_percent SMALLINT DEFAULT 40,
        max_tickets_per_cpf SMALLINT,
        status CHECK IN ('DRAFT','PUBLISHED','ENDED','CANCELLED'), published_at)
event_media (id, event_id FK, organization_id, kind CHECK IN ('FLYER','GALLERY'), url, position)
event_lineup (id, event_id FK, organization_id, name, position)
```

### ticketing
```sql
ticket_types (id, event_id FK, organization_id, name, description, is_half_price BOOLEAN, position)
ticket_batches (id, ticket_type_id FK, event_id, organization_id, name, price_cents,
                capacity INT, sold INT DEFAULT 0, reserved INT DEFAULT 0,
                sales_start_at, sales_end_at, max_per_order SMALLINT, visible BOOLEAN,
                status CHECK IN ('SCHEDULED','ON_SALE','SOLD_OUT','CLOSED'), position,
                CHECK (sold + reserved <= capacity))
tickets (id, order_item_id FK, event_id, organization_id, ticket_batch_id,
         holder_name, holder_cpf_encrypted, holder_cpf_hash,
         token_hash UNIQUE, status CHECK IN ('VALID','CHECKED_IN','TRANSFERRED','CANCELLED'))
```

### order
```sql
orders (id, event_id FK, organization_id, buyer_user_id NULL,
        buyer_name, buyer_email, buyer_phone, buyer_cpf_encrypted, buyer_cpf_hash,
        promoter_id NULL, subtotal_cents, fee_cents, discount_cents, total_cents,
        status CHECK IN ('PENDING_PAYMENT','PAID','EXPIRED','FAILED','REFUNDED','PARTIALLY_REFUNDED','CHARGEBACK'),
        expires_at, paid_at, idempotency_key UNIQUE)
order_items (id, order_id FK, ticket_batch_id FK, unit_price_cents, fee_cents,
             holder_name, holder_cpf_encrypted, holder_cpf_hash, is_half_price)
```

### payment
```sql
payments (id, order_id FK, organization_id, provider, provider_payment_id UNIQUE,
          method CHECK IN ('PIX','CARD'), status, amount_cents, platform_fee_cents,
          raw_payload JSONB, approved_at)
refunds (id, payment_id FK, order_id, amount_cents, reason, provider_refund_id, status, created_by)
```

### promoter
```sql
promoters (id, organization_id FK, user_id NULL, name, phone)
promoter_event_links (id, promoter_id FK, event_id FK, organization_id, code,
                      commission_type CHECK IN ('FIXED','PERCENT'), commission_value,
                      UNIQUE(event_id, code))
```

### checkin
```sql
checkin_gates (id, event_id FK, organization_id, name)
checkins (id, ticket_id FK, event_id, organization_id, gate_id NULL, operator_user_id,
          checked_in_at, source CHECK IN ('ONLINE','OFFLINE'), device_id, undone_at NULL, undone_by NULL)
-- índice único parcial: um check-in ativo por ingresso
CREATE UNIQUE INDEX ux_checkins_active ON checkins(ticket_id) WHERE undone_at IS NULL;
```

### compliance e infra
```sql
terms_versions (id, kind CHECK IN ('TERMS','PRIVACY'), version, published_at, url)
consent_records (id, user_id NULL, email, terms_version_id FK, purpose, granted, ip, user_agent, created_at)
audit_logs (id, organization_id NULL, actor_user_id, action, entity, entity_id, data JSONB, created_at)
outbox_events (id, type, aggregate_id, payload JSONB, status CHECK IN ('PENDING','DONE','FAILED'),
               attempts INT DEFAULT 0, next_attempt_at, created_at)
```

## Índices essenciais

- `events(organization_id, starts_at)`, `events(status, starts_at)`
- `ticket_batches(event_id, position)`
- `orders(event_id, status)`, `orders(status, expires_at)` (job de expiração), `orders(buyer_cpf_hash, event_id)` (limite por CPF)
- `tickets(event_id, status)`, `tickets(holder_cpf_hash)`
- `outbox_events(status, next_attempt_at)`

## Backups

Backup diário automático + PITR do provedor. **Restauração testada** antes do piloto (PLAN M9).
