ALTER TABLE lesson_progress
    ADD CONSTRAINT ck_lesson_progress_score CHECK (score BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_lesson_progress_accuracy CHECK (accuracy BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_lesson_progress_attempts CHECK (attempts >= 1);
ALTER TABLE user_words
    ADD CONSTRAINT ck_user_words_interval CHECK (interval_days >= 0),
    ADD CONSTRAINT ck_user_words_repetitions CHECK (repetitions >= 0);
ALTER TABLE review_sessions
    ADD CONSTRAINT ck_review_sessions_totals CHECK (total >= 0 AND correct >= 0 AND correct <= total),
    ADD CONSTRAINT ck_review_sessions_xp CHECK (xp_earned >= 0),
    ADD CONSTRAINT ck_review_sessions_time CHECK (finished_at >= started_at);
ALTER TABLE subtitles ADD CONSTRAINT ck_subtitles_time CHECK (start_ms >= 0 AND end_ms > start_ms);
ALTER TABLE story_lines ADD CONSTRAINT ck_story_lines_time CHECK (
    (start_ms IS NULL AND end_ms IS NULL) OR
    (start_ms IS NOT NULL AND end_ms IS NOT NULL AND start_ms >= 0 AND end_ms > start_ms));
ALTER TABLE plans ADD CONSTRAINT ck_plans_price CHECK (price_cents >= 0);
ALTER TABLE payments ADD CONSTRAINT ck_payments_amount CHECK (amount_cents >= 0);
ALTER TABLE streaks ADD CONSTRAINT ck_streaks_counts CHECK (current >= 0 AND longest >= current);
ALTER TABLE lessons ADD CONSTRAINT ck_lessons_xp CHECK (xp_reward >= 0);
ALTER TABLE media_clips ADD CONSTRAINT ck_media_clips_duration CHECK (duration_ms >= 0);
