-- P0 learning integrity: immutable request receipts and idempotent rewards.
CREATE TABLE lesson_attempts (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    lesson_id  UUID        NOT NULL REFERENCES lessons(id) ON DELETE RESTRICT,
    score      SMALLINT    NOT NULL CHECK (score BETWEEN 0 AND 100),
    accuracy   SMALLINT    NOT NULL CHECK (accuracy BETWEEN 0 AND 100),
    status     VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    result     JSONB,
    saved_at   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lesson_attempts_user_created ON lesson_attempts(user_id, created_at DESC);

ALTER TABLE xp_events ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX ux_xp_events_idempotency
    ON xp_events(user_id, source, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE review_sessions ADD COLUMN streak_after INTEGER;
ALTER TABLE review_answers
    ADD CONSTRAINT uq_review_answers_session_word UNIQUE (session_id, word_id);
