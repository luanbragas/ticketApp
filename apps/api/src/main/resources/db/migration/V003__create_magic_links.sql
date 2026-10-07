-- M1: link mágico (SECURITY.md §Autenticação, ADR-004).
-- Guarda só o hash SHA-256 do token; o token em si só existe no e-mail enviado.

CREATE TABLE magic_links (
    id         UUID        PRIMARY KEY,
    email      TEXT        NOT NULL CHECK (email = lower(email)),
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Limite de links por e-mail numa janela de tempo.
CREATE INDEX ix_magic_links_email_created_at ON magic_links (email, created_at);

-- ADR-004: comprador que entra por link mágico ganha conta sem nome; o nome vem depois.
ALTER TABLE users ALTER COLUMN name DROP NOT NULL;
