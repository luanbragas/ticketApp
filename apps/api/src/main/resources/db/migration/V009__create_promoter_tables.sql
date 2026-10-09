-- M7: promoters, links por evento, atribuição no pedido e vendas por promoter (docs/DATABASE.md, ADR-009).

CREATE TABLE promoters (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    -- Membro com papel PROMOTER que vê as próprias vendas no painel. Nulo = promoter só com link.
    user_id         UUID        NULL REFERENCES users (id),
    name            TEXT        NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 80),
    phone           TEXT        NULL CHECK (phone ~ '^\+?[0-9]{10,15}$'),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, organization_id),
    UNIQUE (organization_id, user_id)
);

CREATE TABLE promoter_event_links (
    id              UUID        PRIMARY KEY,
    promoter_id     UUID        NOT NULL,
    event_id        UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    -- Vai no link: /e/{slug}?p={code}. Minúsculo, sem dado pessoal além do apelido escolhido.
    code            TEXT        NOT NULL CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(code) BETWEEN 3 AND 30),
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (promoter_id, organization_id) REFERENCES promoters (id, organization_id),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id),
    UNIQUE (event_id, code),
    UNIQUE (event_id, promoter_id)
);

-- Atribuição: último link clicado nos 7 dias antes da compra (ADR-009).
ALTER TABLE orders ADD COLUMN promoter_id UUID NULL REFERENCES promoters (id);
CREATE INDEX ix_orders_promoter_id ON orders (promoter_id) WHERE promoter_id IS NOT NULL;

-- Vendas pagas por promoter, alimentadas pelo evento OrderPaid (o módulo promoter não lê a tabela orders).
CREATE TABLE promoter_sales (
    order_id        UUID        PRIMARY KEY,
    promoter_id     UUID        NOT NULL REFERENCES promoters (id),
    event_id        UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    tickets         INT         NOT NULL CHECK (tickets > 0),
    -- Centavos: valor dos ingressos, sem a taxa de serviço (é o que o produtor recebe).
    revenue_cents   BIGINT      NOT NULL CHECK (revenue_cents >= 0),
    paid_at         TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id)
);

CREATE INDEX ix_promoter_sales_event_id_promoter_id ON promoter_sales (event_id, promoter_id);
