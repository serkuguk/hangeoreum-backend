CREATE INDEX idx_lesson_progress_lesson ON lesson_progress(lesson_id);
CREATE INDEX idx_lesson_attempts_lesson ON lesson_attempts(lesson_id);
CREATE INDEX idx_user_words_word ON user_words(word_id);
CREATE INDEX idx_decks_user_created ON decks(user_id, created_at);
CREATE INDEX idx_payments_subscription ON payments(subscription_id);
DROP INDEX idx_media_clips_kind;
CREATE INDEX idx_media_clips_feed ON media_clips(kind, created_at DESC, id DESC) WHERE is_published;
