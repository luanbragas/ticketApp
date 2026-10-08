-- M3: tipos de ingresso e lotes (docs/DATABASE.md, ADR-006).
-- Tipo e lote carregam (event_id, organization_id) juntos: nunca apontam para evento de outra organização.
-- sold e reserved só mudam por UPDATE condicional atômico (ARCHITECTURE.md §Estoque), nunca pela entidade.

CREATE TABLE ticket_types (
    id              UUID        PRIMARY KEY,
    event_id        UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    name            TEXT        NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 60),
    description     TEXT        NULL CHECK (length(description) <= 500),
    -- Meia-entrada: lotes deste tipo contam para a cota de meia do evento.
    is_half_price   BOOLEAN     NOT NULL DEFAULT false,
    position        SMALLINT    NOT NULL CHECK (position >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id),
    UNIQUE (id, event_id, organization_id),
    UNIQUE (event_id, position)
);

CREATE TABLE ticket_batches (
    id              UUID        PRIMARY KEY,
    ticket_type_id  UUID        NOT NULL,
    event_id        UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    name            TEXT        NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 60),
    -- Centavos. Gratuito (cortesia) não passa por lote pago.
    price_cents     BIGINT      NOT NULL CHECK (price_cents BETWEEN 1 AND 100000000),
    capacity        INT         NOT NULL CHECK (capacity BETWEEN 1 AND 100000),
    sold            INT         NOT NULL DEFAULT 0 CHECK (sold >= 0),
    reserved        INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    -- Não abre antes de sales_start_at; vira (fecha) em sales_end_at.
    sales_start_at  TIMESTAMPTZ NULL,
    sales_end_at    TIMESTAMPTZ NULL,
    max_per_order   SMALLINT    NULL CHECK (max_per_order BETWEEN 1 AND 20),
    visible         BOOLEAN     NOT NULL DEFAULT true,
    status          TEXT        NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'ON_SALE', 'SOLD_OUT', 'CLOSED')),
    position        SMALLINT    NOT NULL CHECK (position >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (ticket_type_id, event_id, organization_id) REFERENCES ticket_types (id, event_id, organization_id),
    UNIQUE (ticket_type_id, position),
    CHECK (sold + reserved <= capacity),
    CHECK (sales_start_at IS NULL OR sales_end_at IS NULL OR sales_end_at > sales_start_at)
);

CREATE INDEX ix_ticket_batches_event_id ON ticket_batches (event_id);
-- Job de virada por data: só olha lote que ainda pode mudar.
CREATE INDEX ix_ticket_batches_pending ON ticket_batches (status) WHERE status IN ('SCHEDULED', 'ON_SALE');
-- No máximo um lote à venda por tipo (ADR-006). Adiado para o commit: a virada fecha um lote e abre o
-- próximo na mesma transação, em qualquer ordem de UPDATE.
ALTER TABLE ticket_batches ADD CONSTRAINT ex_ticket_batches_one_on_sale
    EXCLUDE USING btree (ticket_type_id WITH =) WHERE (status = 'ON_SALE') DEFERRABLE INITIALLY DEFERRED;
