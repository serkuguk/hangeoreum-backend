ALTER TABLE daily_activity
    ADD COLUMN lesson_reservations integer NOT NULL DEFAULT 0 CHECK (lesson_reservations >= 0),
    ADD COLUMN game_sessions_started integer NOT NULL DEFAULT 0 CHECK (game_sessions_started >= 0);

-- Preserve the old totals and include accepted receipts whose XP outbox is still pending.
UPDATE daily_activity SET lesson_reservations = lessons_completed;
INSERT INTO daily_activity (user_id, activity_date, lesson_reservations)
SELECT user_id, created_at::date, count(*) FROM lesson_attempts GROUP BY user_id, created_at::date
ON CONFLICT (user_id, activity_date) DO UPDATE
SET lesson_reservations = GREATEST(daily_activity.lesson_reservations, EXCLUDED.lesson_reservations);

-- Games consume quota at creation, independently of review XP or session completion.
INSERT INTO daily_activity (user_id, activity_date, game_sessions_started)
SELECT user_id, started_at::date, count(*) FROM review_sessions
WHERE mode IN ('MATCH', 'LISTEN', 'SPELL') GROUP BY user_id, started_at::date
ON CONFLICT (user_id, activity_date) DO UPDATE
SET game_sessions_started = EXCLUDED.game_sessions_started;

CREATE TABLE review_session_cards (
    session_id uuid NOT NULL REFERENCES review_sessions(id) ON DELETE CASCADE,
    word_id uuid NOT NULL REFERENCES words(id) ON DELETE RESTRICT,
    position integer NOT NULL CHECK (position >= 0),
    PRIMARY KEY (session_id, word_id),
    UNIQUE (session_id, position)
);
CREATE INDEX idx_review_session_cards_word ON review_session_cards(word_id);

-- Issued sets for old open sessions cannot be reconstructed safely. Close without awarding XP;
-- users start a fresh session. Already awarded/finished sessions retain their idempotent receipt.
UPDATE review_sessions SET finished_at = CURRENT_TIMESTAMP WHERE finished_at IS NULL;
