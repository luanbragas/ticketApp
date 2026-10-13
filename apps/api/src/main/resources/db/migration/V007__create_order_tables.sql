-- M4: pedidos e itens (docs/DATABASE.md, ADR-007).
-- Pedido ≠ ingresso: o ingresso só nasce depois do pagamento (M6). O pedido segura o estoque como reserva.
-- CPF só criptografado (AES-GCM) + hash HMAC para busca; nunca em claro (SECURITY.md).

CREATE TABLE orders (
    id                  UUID        PRIMARY KEY,
    event_id            UUID        NOT NULL,
    organization_id     UUID        NOT NULL,
    buyer_name          TEXT        NOT NULL CHECK (length(trim(buyer_name)) BETWEEN 1 AND 120),
    buyer_email         TEXT        NOT NULL CHECK (length(buyer_email) BETWEEN 3 AND 254),
    buyer_phone         TEXT        NULL CHECK (buyer_phone ~ '^\+?[0-9]{10,15}$'),
    buyer_cpf_encrypted BYTEA       NOT NULL,
    buyer_cpf_hash      TEXT        NOT NULL,
    -- Centavos. Taxa de serviço paga pelo comprador, por cima do preço (ADR-007).
    subtotal_cents      BIGINT      NOT NULL CHECK (subtotal_cents >= 0),
    fee_cents           BIGINT      NOT NULL CHECK (fee_cents >= 0),
    discount_cents      BIGINT      NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
    total_cents         BIGINT      NOT NULL CHECK (total_cents >= 0),
    status              TEXT        NOT NULL DEFAULT 'PENDING_PAYMENT'
                        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'EXPIRED', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED', 'CHARGEBACK')),
    expires_at          TIMESTAMPTZ NOT NULL,
    paid_at             TIMESTAMPTZ NULL,
    expired_at          TIMESTAMPTZ NULL,
    -- SHA-256 do Idempotency-Key que o navegador gera (aleatório, ≥ 32 caracteres). A mesma chave repete o
    -- pedido sem duplicar e é o segredo para acompanhar o status (ADR-007). A chave em si nunca é guardada.
    access_key_hash     TEXT        NOT NULL UNIQUE,
    adult_declared      BOOLEAN     NOT NULL,
    terms_version       TEXT        NOT NULL,
    terms_accepted_at   TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id),
    CHECK (total_cents = subtotal_cents + fee_cents - discount_cents),
    CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
    CHECK (status <> 'EXPIRED' OR expired_at IS NOT NULL)
);

CREATE INDEX ix_orders_event_id_status ON orders (event_id, status);
-- Job de expiração.
CREATE INDEX ix_orders_pending_expires_at ON orders (expires_at) WHERE status = 'PENDING_PAYMENT';
-- Limite por CPF no evento.
CREATE INDEX ix_orders_buyer_cpf_hash_event_id ON orders (buyer_cpf_hash, event_id);

CREATE TABLE order_items (
    id                   UUID        PRIMARY KEY,
    order_id             UUID        NOT NULL REFERENCES orders (id),
    ticket_batch_id      UUID        NOT NULL REFERENCES ticket_batches (id),
    -- Preço e taxa congelados no momento da reserva.
    unit_price_cents     BIGINT      NOT NULL CHECK (unit_price_cents > 0),
    fee_cents            BIGINT      NOT NULL CHECK (fee_cents >= 0),
    holder_name          TEXT        NOT NULL CHECK (length(trim(holder_name)) BETWEEN 1 AND 120),
    holder_cpf_encrypted BYTEA       NOT NULL,
    holder_cpf_hash      TEXT        NOT NULL,
    is_half_price        BOOLEAN     NOT NULL,
    -- Meia: o titular declara o benefício e mostra o documento na entrada.
    half_price_reason    TEXT        NULL CHECK (half_price_reason IN ('STUDENT', 'PCD', 'YOUTH_LOW_INCOME', 'SENIOR')),
    position             SMALLINT    NOT NULL CHECK (position >= 0),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, position),
    CHECK (is_half_price = (half_price_reason IS NOT NULL))
);

CREATE INDEX ix_order_items_order_id ON order_items (order_id);
CREATE INDEX ix_order_items_ticket_batch_id ON order_items (ticket_batch_id);
