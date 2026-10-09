-- M6: outbox de eventos de domínio e ingressos emitidos (docs/DATABASE.md, ADR-008).

-- Evento gravado na mesma transação da mudança de estado; o worker entrega depois (ARCHITECTURE.md §Outbox).
CREATE TABLE outbox_events (
    id              UUID        PRIMARY KEY,
    type            TEXT        NOT NULL CHECK (length(type) BETWEEN 1 AND 80),
    aggregate_id    UUID        NOT NULL,
    payload         JSONB       NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    attempts        INT         NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error      TEXT        NULL,
    processed_at    TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_outbox_events_pending ON outbox_events (next_attempt_at) WHERE status = 'PENDING';

-- Ingresso só nasce depois do pagamento: um por item do pedido (CLAUDE.md regra 6).
CREATE TABLE tickets (
    id                   UUID        PRIMARY KEY,
    order_item_id        UUID        NOT NULL UNIQUE REFERENCES order_items (id),
    order_id             UUID        NOT NULL REFERENCES orders (id),
    event_id             UUID        NOT NULL,
    organization_id      UUID        NOT NULL,
    ticket_batch_id      UUID        NOT NULL REFERENCES ticket_batches (id),
    -- E-mail de quem comprou (minúsculo): "Meus ingressos" lista por ele, com e-mail verificado (ADR-008).
    buyer_email          TEXT        NOT NULL,
    holder_name          TEXT        NOT NULL CHECK (length(trim(holder_name)) BETWEEN 1 AND 120),
    holder_cpf_encrypted BYTEA       NOT NULL,
    holder_cpf_hash      TEXT        NOT NULL,
    is_half_price        BOOLEAN     NOT NULL,
    -- QR: token = HMAC(chave, nonce). O banco guarda o nonce e o hash do token, nunca o token (SECURITY.md).
    token_nonce          BYTEA       NOT NULL,
    token_hash           TEXT        NOT NULL UNIQUE,
    status               TEXT        NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID', 'CHECKED_IN', 'TRANSFERRED', 'CANCELLED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id)
);

CREATE INDEX ix_tickets_event_id_status ON tickets (event_id, status);
CREATE INDEX ix_tickets_holder_cpf_hash ON tickets (holder_cpf_hash);
CREATE INDEX ix_tickets_buyer_email ON tickets (buyer_email);
CREATE INDEX ix_tickets_order_id ON tickets (order_id);
