-- M8: check-ins e audit log (docs/DATABASE.md, ADR-010).

-- Ações sensíveis ficam registradas para sempre (SECURITY.md §Auditoria).
CREATE TABLE audit_logs (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NULL REFERENCES organizations (id),
    actor_user_id   UUID        NULL REFERENCES users (id),
    action          TEXT        NOT NULL CHECK (length(action) BETWEEN 1 AND 80),
    entity          TEXT        NOT NULL CHECK (length(entity) BETWEEN 1 AND 80),
    entity_id       UUID        NULL,
    data            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_organization_id_created_at ON audit_logs (organization_id, created_at);
CREATE INDEX ix_audit_logs_entity_id ON audit_logs (entity_id);

-- Cada leitura na portaria vira uma linha. Só uma por ingresso vale (ativa); as outras ficam como
-- duplicada (outro aparelho leu antes) para o relatório de conflitos do modo offline.
CREATE TABLE checkins (
    id               UUID        PRIMARY KEY,
    ticket_id        UUID        NOT NULL REFERENCES tickets (id),
    event_id         UUID        NOT NULL,
    organization_id  UUID        NOT NULL,
    operator_user_id UUID        NOT NULL REFERENCES users (id),
    -- Hora da leitura no aparelho (offline) ou no servidor (online).
    checked_in_at    TIMESTAMPTZ NOT NULL,
    source           TEXT        NOT NULL CHECK (source IN ('ONLINE', 'OFFLINE')),
    device_id        TEXT        NULL CHECK (length(device_id) <= 64),
    -- Leitura que perdeu para outra mais antiga do mesmo ingresso.
    duplicate        BOOLEAN     NOT NULL DEFAULT false,
    undone_at        TIMESTAMPTZ NULL,
    undone_by        UUID        NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id),
    CHECK ((undone_at IS NULL) = (undone_by IS NULL))
);

-- Um check-in valendo por ingresso.
CREATE UNIQUE INDEX ux_checkins_active ON checkins (ticket_id) WHERE undone_at IS NULL AND NOT duplicate;
CREATE INDEX ix_checkins_event_id_checked_in_at ON checkins (event_id, checked_in_at);
