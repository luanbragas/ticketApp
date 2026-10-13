-- M9: reembolso manual e estornos que chegam do gateway (chargeback, estorno pelo painel do Mercado Pago).

-- Dinheiro devolvido ao comprador. Nunca apagado (DATABASE.md: dado financeiro não se apaga).
CREATE TABLE refunds (
    id                 UUID        PRIMARY KEY,
    payment_id         UUID        NOT NULL REFERENCES payments (id),
    order_id           UUID        NOT NULL REFERENCES orders (id),
    organization_id    UUID        NOT NULL REFERENCES organizations (id),
    -- REFUND: pedido pelo produtor (aqui) ou estornado no painel do gateway; CHARGEBACK: contestação do comprador.
    kind               TEXT        NOT NULL CHECK (kind IN ('REFUND', 'CHARGEBACK')),
    amount_cents       BIGINT      NOT NULL CHECK (amount_cents > 0),
    reason             TEXT        NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 500),
    -- Nulo quando o estorno nasceu fora da plataforma (aviso do gateway).
    created_by         UUID        NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Um estorno por pagamento (só reembolso total no MVP).
    UNIQUE (payment_id)
);

CREATE INDEX ix_refunds_order_id ON refunds (order_id);

-- Venda estornada sai da conta do promoter, mas a linha fica (histórico).
ALTER TABLE promoter_sales ADD COLUMN refunded_at TIMESTAMPTZ NULL;
