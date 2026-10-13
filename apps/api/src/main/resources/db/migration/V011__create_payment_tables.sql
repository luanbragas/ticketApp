-- M5: conta de recebimento do produtor, OAuth e pagamentos (docs/DATABASE.md, ADR-003, ADR-011).

-- Conta do produtor no gateway (Mercado Pago via OAuth): o dinheiro cai direto nela, e a plataforma retém a taxa
-- de serviço como application_fee. Tokens só cifrados (SECURITY.md §Pagamentos).
CREATE TABLE payment_accounts (
    id                      UUID        PRIMARY KEY,
    organization_id         UUID        NOT NULL UNIQUE REFERENCES organizations (id),
    provider                TEXT        NOT NULL CHECK (provider IN ('MERCADO_PAGO', 'FAKE')),
    provider_user_id        TEXT        NOT NULL,
    public_key              TEXT        NULL,
    access_token_encrypted  BYTEA       NOT NULL,
    refresh_token_encrypted BYTEA       NULL,
    expires_at              TIMESTAMPTZ NULL,
    connected_by            UUID        NOT NULL REFERENCES users (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "state" do OAuth: uso único, curto, preso a quem iniciou (proteção contra CSRF no retorno).
CREATE TABLE payment_oauth_states (
    state_hash      TEXT        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Cada tentativa de cobrança de um pedido (PIX gerado, cartão recusado e tentado de novo...).
CREATE TABLE payments (
    id                  UUID        PRIMARY KEY,
    order_id            UUID        NOT NULL REFERENCES orders (id),
    organization_id     UUID        NOT NULL REFERENCES organizations (id),
    provider            TEXT        NOT NULL CHECK (provider IN ('MERCADO_PAGO', 'FAKE')),
    provider_payment_id TEXT        NOT NULL,
    idempotency_key     TEXT        NOT NULL UNIQUE,
    method              TEXT        NOT NULL CHECK (method IN ('PIX', 'CARD')),
    status              TEXT        NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'REFUNDED', 'CHARGED_BACK')),
    status_detail       TEXT        NULL,
    -- Centavos: total cobrado do comprador e taxa da plataforma retida no split.
    amount_cents        BIGINT      NOT NULL CHECK (amount_cents > 0),
    platform_fee_cents  BIGINT      NOT NULL CHECK (platform_fee_cents >= 0),
    pix_qr_code         TEXT        NULL,
    pix_qr_base64       TEXT        NULL,
    pix_expires_at      TIMESTAMPTZ NULL,
    approved_at         TIMESTAMPTZ NULL,
    -- Último retrato do pagamento no gateway (auditoria e conciliação). Sem dado de cartão.
    raw_payload         JSONB       NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_payment_id),
    CHECK (status <> 'APPROVED' OR approved_at IS NOT NULL)
);

CREATE INDEX ix_payments_order_id ON payments (order_id);
