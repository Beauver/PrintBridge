CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Access tokens issued before this moment are rejected (password reset).
    credentials_changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Usernames and emails are unique regardless of case.
CREATE UNIQUE INDEX users_username_uq ON users (lower(username));
CREATE UNIQUE INDEX users_email_uq ON users (lower(email));

-- There is exactly one owner; this also makes concurrent first-run setups safe.
CREATE UNIQUE INDEX users_single_admin_uq ON users (role) WHERE role = 'ADMIN';

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
