-- M1: convite de membro por e-mail com papel.
-- Guarda só o hash SHA-256 do token. OWNER não é convidável (a posse é transferida, não convidada).

CREATE TABLE organization_invitations (
    id                  UUID        PRIMARY KEY,
    organization_id     UUID        NOT NULL REFERENCES organizations (id),
    email               TEXT        NOT NULL CHECK (email = lower(email)),
    role                TEXT        NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'PROMOTER', 'CHECKIN_OPERATOR')),
    token_hash          TEXT        NOT NULL UNIQUE,
    invited_by_user_id  UUID        NOT NULL REFERENCES users (id),
    expires_at          TIMESTAMPTZ NOT NULL,
    accepted_at         TIMESTAMPTZ NULL,
    accepted_by_user_id UUID        NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((accepted_at IS NULL) = (accepted_by_user_id IS NULL))
);

CREATE INDEX ix_organization_invitations_organization_id ON organization_invitations (organization_id);
