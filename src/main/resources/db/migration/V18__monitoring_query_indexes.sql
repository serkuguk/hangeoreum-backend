-- Existing session/result/card indexes already cover their owner queries.
CREATE INDEX idx_lesson_attempts_user_created_id ON lesson_attempts (user_id, created_at DESC, id DESC);
CREATE INDEX idx_lesson_progress_user_updated_lesson ON lesson_progress (user_id, updated_at DESC, lesson_id DESC);
CREATE INDEX idx_review_sessions_user_started_id ON review_sessions (user_id, started_at DESC, id DESC);
CREATE INDEX idx_review_answers_session_answered_id ON review_answers (session_id, answered_at, id);
CREATE INDEX idx_user_words_difficult_user_word ON user_words (user_id, word_id, id) WHERE is_difficult;
