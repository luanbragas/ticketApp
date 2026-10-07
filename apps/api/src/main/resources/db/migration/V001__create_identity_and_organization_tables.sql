-- M1: identidade e organização (docs/DATABASE.md).
-- IDs são UUIDv7 gerados na aplicação; updated_at é mantido pela aplicação.

CREATE TABLE users (
    id                UUID        PRIMARY KEY,
    name              TEXT        NOT NULL CHECK (length(trim(name)) > 0),
    -- Guardado normalizado em minúsculas para a unicidade não depender de caixa.
    email             TEXT        NOT NULL UNIQUE CHECK (email = lower(email) AND length(email) BETWEEN 3 AND 320),
    password_hash     TEXT        NULL,
    email_verified_at TIMESTAMPTZ NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_identities (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id),
    provider         TEXT        NOT NULL CHECK (provider IN ('GOOGLE')),
    provider_user_id TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX ix_user_identities_user_id ON user_identities (user_id);

CREATE TABLE organizations (
    id         UUID        PRIMARY KEY,
    name       TEXT        NOT NULL CHECK (length(trim(name)) > 0),
    slug       TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) BETWEEN 3 AND 60),
    logo_url   TEXT        NULL,
    instagram  TEXT        NULL,
    whatsapp   TEXT        NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE organization_members (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    role            TEXT        NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'PROMOTER', 'CHECKIN_OPERATOR')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id)
);

-- "Minhas organizações" do usuário logado; consultas por organização usam o índice do UNIQUE.
CREATE INDEX ix_organization_members_user_id ON organization_members (user_id);
