-- Ідемпотентна схема ecoportal (PostgreSQL 16). Виконується при кожному старті.

CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('PARTICIPANT', 'COORDINATOR'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (LOWER(email));

CREATE TABLE IF NOT EXISTS initiatives (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coordinator_id UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    title          VARCHAR(200) NOT NULL,
    description    TEXT         NOT NULL,
    category       VARCHAR(100) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_initiatives_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX IF NOT EXISTS idx_initiatives_coordinator ON initiatives (coordinator_id);

CREATE TABLE IF NOT EXISTS events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    initiative_id         UUID             NOT NULL REFERENCES initiatives (id) ON DELETE RESTRICT,
    title                 VARCHAR(200)     NOT NULL,
    description           TEXT             NOT NULL,
    city                  VARCHAR(100)     NOT NULL,
    address_text          VARCHAR(300)     NOT NULL,
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    starts_at             TIMESTAMPTZ      NOT NULL,
    ends_at               TIMESTAMPTZ      NOT NULL,
    registration_closes_at TIMESTAMPTZ     NOT NULL,
    participants_limit    INT              NOT NULL,
    status                VARCHAR(20)      NOT NULL DEFAULT 'DRAFT',
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT chk_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT chk_events_limit CHECK (participants_limit > 0),
    CONSTRAINT chk_events_period CHECK (ends_at > starts_at),
    CONSTRAINT chk_events_registration CHECK (registration_closes_at <= starts_at),
    CONSTRAINT chk_events_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_events_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX IF NOT EXISTS idx_events_status_starts ON events (status, starts_at);
CREATE INDEX IF NOT EXISTS idx_events_city_lower ON events (LOWER(city));
CREATE INDEX IF NOT EXISTS idx_events_initiative ON events (initiative_id);

CREATE TABLE IF NOT EXISTS applications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id   UUID         NOT NULL REFERENCES events (id) ON DELETE RESTRICT,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    comment    VARCHAR(500),
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    decided_by UUID         REFERENCES users (id) ON DELETE RESTRICT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_applications_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_applications_decision CHECK ((decided_by IS NULL) = (decided_at IS NULL)),
    CONSTRAINT uq_applications_event_user UNIQUE (event_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_applications_event_status ON applications (event_id, status);
CREATE INDEX IF NOT EXISTS idx_applications_user ON applications (user_id);

CREATE TABLE IF NOT EXISTS event_summaries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID           NOT NULL UNIQUE REFERENCES events (id) ON DELETE RESTRICT,
    actual_participants INT            NOT NULL DEFAULT 0,
    waste_kg            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    trees_planted       INT            NOT NULL DEFAULT 0,
    hours_spent         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    note                VARCHAR(1000),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_summaries_participants CHECK (actual_participants >= 0),
    CONSTRAINT chk_summaries_waste CHECK (waste_kg >= 0),
    CONSTRAINT chk_summaries_trees CHECK (trees_planted >= 0),
    CONSTRAINT chk_summaries_hours CHECK (hours_spent >= 0)
);
