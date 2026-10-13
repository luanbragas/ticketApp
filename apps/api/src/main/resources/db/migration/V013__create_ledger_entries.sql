-- M9: financeiro básico. Livro de lançamentos do módulo finance, alimentado pelos eventos OrderPaid e
-- OrderRefunded (o módulo não lê as tabelas de pedido). Nunca apagado nem editado: estorno é lançamento negativo.
CREATE TABLE ledger_entries (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    event_id        UUID        NOT NULL,
    order_id        UUID        NOT NULL,
    -- TICKET_*: valor dos ingressos (do produtor). SERVICE_FEE_*: taxa de serviço paga pelo comprador (da plataforma).
    entry_type      TEXT        NOT NULL CHECK (entry_type IN ('TICKET_SALE', 'SERVICE_FEE', 'TICKET_REFUND', 'SERVICE_FEE_REFUND')),
    -- Centavos com sinal: venda positiva, estorno negativo.
    amount_cents    BIGINT      NOT NULL CHECK (amount_cents <> 0),
    tickets         INT         NOT NULL CHECK (tickets >= 0),
    -- REFUND ou CHARGEBACK nos estornos.
    reason          TEXT        NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Idempotência: o mesmo evento entregue duas vezes lança uma vez só.
    UNIQUE (order_id, entry_type),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id),
    CHECK ((entry_type IN ('TICKET_SALE', 'SERVICE_FEE')) = (amount_cents > 0))
);

CREATE INDEX ix_ledger_entries_event ON ledger_entries (organization_id, event_id);

-- Pedidos pagos antes deste livro existir (migração única; daqui em diante só pelos eventos).
INSERT INTO ledger_entries (id, organization_id, event_id, order_id, entry_type, amount_cents, tickets, occurred_at)
SELECT gen_random_uuid(), o.organization_id, o.event_id, o.id, 'TICKET_SALE', o.subtotal_cents - o.discount_cents,
       (SELECT count(*) FROM order_items i WHERE i.order_id = o.id), o.paid_at
  FROM orders o
 WHERE o.paid_at IS NOT NULL AND o.subtotal_cents - o.discount_cents > 0;

INSERT INTO ledger_entries (id, organization_id, event_id, order_id, entry_type, amount_cents, tickets, occurred_at)
SELECT gen_random_uuid(), o.organization_id, o.event_id, o.id, 'SERVICE_FEE', o.fee_cents, 0, o.paid_at
  FROM orders o
 WHERE o.paid_at IS NOT NULL AND o.fee_cents > 0;

INSERT INTO ledger_entries (id, organization_id, event_id, order_id, entry_type, amount_cents, tickets, reason, occurred_at)
SELECT gen_random_uuid(), o.organization_id, o.event_id, o.id, 'TICKET_REFUND', -(o.subtotal_cents - o.discount_cents),
       (SELECT count(*) FROM order_items i WHERE i.order_id = o.id),
       CASE o.status WHEN 'CHARGEBACK' THEN 'CHARGEBACK' ELSE 'REFUND' END, o.updated_at
  FROM orders o
 WHERE o.paid_at IS NOT NULL AND o.status IN ('REFUNDED', 'CHARGEBACK') AND o.subtotal_cents - o.discount_cents > 0;

INSERT INTO ledger_entries (id, organization_id, event_id, order_id, entry_type, amount_cents, tickets, reason, occurred_at)
SELECT gen_random_uuid(), o.organization_id, o.event_id, o.id, 'SERVICE_FEE_REFUND', -o.fee_cents, 0,
       CASE o.status WHEN 'CHARGEBACK' THEN 'CHARGEBACK' ELSE 'REFUND' END, o.updated_at
  FROM orders o
 WHERE o.paid_at IS NOT NULL AND o.status IN ('REFUNDED', 'CHARGEBACK') AND o.fee_cents > 0;
