-- M9: termos de uso e política de privacidade versionados + registro de aceite (SECURITY.md §LGPD).

-- Cada texto publicado ganha uma versão; a vigente é a mais recente já publicada.
CREATE TABLE terms_versions (
    id           UUID        PRIMARY KEY,
    kind         TEXT        NOT NULL CHECK (kind IN ('TERMS', 'PRIVACY')),
    version      TEXT        NOT NULL CHECK (char_length(version) BETWEEN 1 AND 50),
    -- Caminho da página na web (ex.: /termos).
    url          TEXT        NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (kind, version)
);

-- Prova do aceite: quem, qual versão, para quê, quando e de onde. Nunca apagado nem editado.
CREATE TABLE consent_records (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NULL REFERENCES users (id),
    email            TEXT        NOT NULL,
    terms_version_id UUID        NOT NULL REFERENCES terms_versions (id),
    -- SIGNUP: criar conta; CHECKOUT: comprar (reference_id = pedido).
    purpose          TEXT        NOT NULL CHECK (purpose IN ('SIGNUP', 'CHECKOUT')),
    reference_id     UUID        NULL,
    granted          BOOLEAN     NOT NULL DEFAULT TRUE,
    ip               TEXT        NULL,
    user_agent       TEXT        NULL CHECK (char_length(user_agent) <= 500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_consent_records_email ON consent_records (lower(email));
CREATE INDEX ix_consent_records_reference ON consent_records (reference_id) WHERE reference_id IS NOT NULL;

-- Rascunho até a validação jurídica (PLAN.md M9). A versão final entra por migration nova.
INSERT INTO terms_versions (id, kind, version, url, published_at)
VALUES (gen_random_uuid(), 'TERMS', 'rascunho-2026-10', '/termos', '2026-10-01T00:00:00-03:00'),
       (gen_random_uuid(), 'PRIVACY', 'rascunho-2026-10', '/privacidade', '2026-10-01T00:00:00-03:00');
