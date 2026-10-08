-- M2: eventos, mídia e lineup (docs/DATABASE.md).
-- Rascunho pode ficar sem data e sem local; publicar exige os dois.
-- Mídia e lineup referenciam (event_id, organization_id) juntos: a linha nunca aponta para evento de outra organização.

CREATE TABLE events (
    id                       UUID        PRIMARY KEY,
    organization_id          UUID        NOT NULL REFERENCES organizations (id),
    slug                     TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) BETWEEN 3 AND 80),
    name                     TEXT        NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    description              TEXT        NULL CHECK (length(description) <= 5000),
    category                 TEXT        NULL,
    starts_at                TIMESTAMPTZ NULL,
    ends_at                  TIMESTAMPTZ NULL,
    venue_name               TEXT        NULL,
    address                  TEXT        NULL,
    city                     TEXT        NULL,
    lat                      NUMERIC(9, 6) NULL CHECK (lat BETWEEN -90 AND 90),
    lng                      NUMERIC(9, 6) NULL CHECK (lng BETWEEN -180 AND 180),
    min_age                  SMALLINT    NOT NULL DEFAULT 18 CHECK (min_age BETWEEN 0 AND 21),
    has_open_bar             BOOLEAN     NOT NULL DEFAULT false,
    half_price_quota_percent SMALLINT    NOT NULL DEFAULT 40 CHECK (half_price_quota_percent BETWEEN 0 AND 100),
    max_tickets_per_cpf      SMALLINT    NULL CHECK (max_tickets_per_cpf > 0),
    -- Cor de destaque da página, tirada do flyer (ADR-005). Nula = verde da plataforma.
    accent_color             TEXT        NULL CHECK (accent_color ~ '^#[0-9a-f]{6}$'),
    status                   TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ENDED', 'CANCELLED')),
    published_at             TIMESTAMPTZ NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, organization_id),
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at),
    -- Open bar só para maiores de 18 (docs/PLAN.md, M2).
    CHECK (NOT has_open_bar OR min_age >= 18),
    CHECK (status <> 'PUBLISHED' OR (published_at IS NOT NULL AND starts_at IS NOT NULL AND ends_at IS NOT NULL AND venue_name IS NOT NULL))
);

CREATE INDEX ix_events_organization_id_starts_at ON events (organization_id, starts_at);
CREATE INDEX ix_events_status_starts_at ON events (status, starts_at);

CREATE TABLE event_media (
    id              UUID        PRIMARY KEY,
    event_id        UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    kind            TEXT        NOT NULL CHECK (kind IN ('FLYER', 'GALLERY')),
    url             TEXT        NOT NULL CHECK (length(url) BETWEEN 1 AND 2048),
    -- Dimensões em px: definem o formato da capa (story, feed ou deitada) sem baixar a imagem.
    width           INT         NULL CHECK (width > 0),
    height          INT         NULL CHECK (height > 0),
    position        SMALLINT    NOT NULL DEFAULT 0 CHECK (position >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id),
    UNIQUE (event_id, kind, position)
);

-- Um flyer por evento.
CREATE UNIQUE INDEX ux_event_media_one_flyer ON event_media (event_id) WHERE kind = 'FLYER';

CREATE TABLE event_lineup (
    id              UUID        PRIMARY KEY,
    event_id        UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    name            TEXT        NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    -- Horário na programação ("A noite"); opcional.
    starts_at       TIMESTAMPTZ NULL,
    position        SMALLINT    NOT NULL CHECK (position >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, organization_id) REFERENCES events (id, organization_id),
    UNIQUE (event_id, position)
);
