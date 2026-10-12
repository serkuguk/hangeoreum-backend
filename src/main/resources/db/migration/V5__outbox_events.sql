CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    type         VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    attempts     INTEGER      NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    last_error   TEXT
);
CREATE INDEX idx_outbox_events_ready ON outbox_events (available_at, occurred_at) WHERE status = 'PENDING';

CREATE TABLE processed_events (
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID         NOT NULL REFERENCES outbox_events(id) ON DELETE CASCADE,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

ALTER TABLE lesson_attempts
    ADD COLUMN new_words JSONB,
    ADD COLUMN xp INTEGER,
    ADD COLUMN streak INTEGER,
    ADD COLUMN goal_reached BOOLEAN;
