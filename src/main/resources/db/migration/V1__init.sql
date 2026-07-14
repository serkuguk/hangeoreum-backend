-- =====================================================================
-- 한걸음 (Hangeoreum) — PostgreSQL schema
-- Bounded Contexts: Identity, Learning, Vocabulary, Media,
--                   Gamification, Notifications, Billing
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

-- ============================= ENUMS =================================

CREATE TYPE user_role        AS ENUM ('USER', 'ADMIN');
CREATE TYPE start_level      AS ENUM ('BEGINNER', 'KNOWS_HANGUL', 'INTERMEDIATE');
CREATE TYPE oauth_provider   AS ENUM ('GOOGLE', 'KAKAO');
CREATE TYPE lesson_type      AS ENUM ('ALPHABET', 'GRAMMAR', 'LESSON', 'STORY');
CREATE TYPE exercise_kind    AS ENUM ('CHOICE', 'LISTEN_CHOICE', 'WORD_ORDER',
                                      'FILL_BLANK', 'MATCH_PAIRS', 'TYPE_WORD');
CREATE TYPE progress_status  AS ENUM ('IN_PROGRESS', 'COMPLETED');
CREATE TYPE letter_group     AS ENUM ('VOWEL_BASIC', 'VOWEL_COMPOUND',
                                      'CONSONANT_BASIC', 'CONSONANT_DOUBLE');
CREATE TYPE review_mode      AS ENUM ('FLASHCARDS', 'MATCH', 'LISTEN', 'SPELL', 'QUICK');
CREATE TYPE clip_kind        AS ENUM ('WORD', 'STORY', 'IMMERSE');
CREATE TYPE xp_source        AS ENUM ('LESSON', 'STORY', 'REVIEW', 'GAME',
                                      'STREAK_BONUS', 'ACHIEVEMENT');
CREATE TYPE notification_type AS ENUM ('REMINDER', 'ACHIEVEMENT', 'STREAK', 'SYSTEM');
CREATE TYPE plan_interval    AS ENUM ('MONTH', 'YEAR', 'LIFETIME');
CREATE TYPE sub_status       AS ENUM ('ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED');
CREATE TYPE pay_provider     AS ENUM ('STRIPE', 'TOSS', 'KAKAOPAY');
CREATE TYPE payment_status   AS ENUM ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED');

-- ======================= IDENTITY CONTEXT ============================

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255),                    -- NULL если только OAuth
    avatar_url    VARCHAR(500),
    role          user_role     NOT NULL DEFAULT 'USER',
    start_level   start_level   NOT NULL DEFAULT 'BEGINNER',
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE oauth_links (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider     oauth_provider NOT NULL,
    provider_uid VARCHAR(255)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_uid),
    UNIQUE (user_id, provider)
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE user_settings (
    user_id            UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    daily_goal_xp      SMALLINT    NOT NULL DEFAULT 20 CHECK (daily_goal_xp IN (10, 20, 50)),
    reminders_enabled  BOOLEAN     NOT NULL DEFAULT FALSE,
    reminder_time      TIME,
    sound_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    autoplay_audio     BOOLEAN     NOT NULL DEFAULT TRUE,
    show_romanization  BOOLEAN     NOT NULL DEFAULT TRUE,
    playback_speed     NUMERIC(3,2) NOT NULL DEFAULT 1.0,
    theme              JSONB       NOT NULL DEFAULT '{}',  -- акцент/масштаб/радиус (theme-config)
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ======================= LEARNING CONTEXT ============================

CREATE TABLE courses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lang         VARCHAR(10)  NOT NULL DEFAULT 'ko',
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    is_published BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE units (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id    UUID         NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    position     SMALLINT     NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    color        VARCHAR(20),                     -- обансэк-цвет узла
    is_published BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (course_id, position)
);

CREATE TABLE lessons (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id      UUID        NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    position     SMALLINT    NOT NULL,
    type         lesson_type NOT NULL DEFAULT 'LESSON',
    title        VARCHAR(200) NOT NULL,
    xp_reward    SMALLINT    NOT NULL DEFAULT 10,
    is_free      BOOLEAN     NOT NULL DEFAULT FALSE, -- доступен на Free-плане
    is_published BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (unit_id, position)
);

-- Learning Tip: 1 грамматический концепт перед уроком
CREATE TABLE learning_tips (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID         NOT NULL UNIQUE REFERENCES lessons(id) ON DELETE CASCADE,
    title     VARCHAR(200) NOT NULL,
    body_md   TEXT         NOT NULL,              -- markdown: правило
    examples  JSONB        NOT NULL DEFAULT '[]'  -- [{ko, translation, highlight:[...]}]
);

CREATE TABLE exercises (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID          NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    position  SMALLINT      NOT NULL,
    kind      exercise_kind NOT NULL,
    payload   JSONB         NOT NULL,   -- структура зависит от kind (см. backend/02-learning.md)
    UNIQUE (lesson_id, position)
);

CREATE TABLE lesson_progress (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID            NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    lesson_id    UUID            NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    status       progress_status NOT NULL DEFAULT 'IN_PROGRESS',
    score        SMALLINT,                        -- 0..100
    accuracy     SMALLINT,                        -- 0..100
    attempts     SMALLINT        NOT NULL DEFAULT 1,
    completed_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (user_id, lesson_id)
);
CREATE INDEX idx_lesson_progress_user ON lesson_progress(user_id);

-- Story: видео-диалог носителей в конце юнита
CREATE TABLE stories (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID NOT NULL UNIQUE REFERENCES lessons(id) ON DELETE CASCADE,
    clip_id   UUID,                               -- FK -> media_clips (добавляется ниже)
    title     VARCHAR(200) NOT NULL
);

CREATE TABLE story_lines (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id         UUID     NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    position         SMALLINT NOT NULL,
    speaker          VARCHAR(100),
    text_ko          TEXT     NOT NULL,
    text_translation TEXT     NOT NULL,
    breakdown        JSONB,                       -- разбор по словам/частицам
    start_ms         INTEGER,
    end_ms           INTEGER,
    UNIQUE (story_id, position)
);

-- Алфавит хангыль (자모): 40 букв в 4 группах
CREATE TABLE alphabet_letters (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jamo         VARCHAR(4)   NOT NULL UNIQUE,    -- ㄱ, ㅏ ...
    romanization VARCHAR(10)  NOT NULL,
    letter_group letter_group NOT NULL,
    position     SMALLINT     NOT NULL,
    audio_url    VARCHAR(500)
);

CREATE TABLE user_letter_progress (
    user_id    UUID        NOT NULL REFERENCES users(id)            ON DELETE CASCADE,
    letter_id  UUID        NOT NULL REFERENCES alphabet_letters(id) ON DELETE CASCADE,
    learned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, letter_id)
);

-- ====================== VOCABULARY CONTEXT ===========================

CREATE TABLE topics (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code  VARCHAR(50)  NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL,
    icon  VARCHAR(50)
);

CREATE TABLE words (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hangul              VARCHAR(100) NOT NULL,
    romanization        VARCHAR(150) NOT NULL,
    translation         VARCHAR(255) NOT NULL,
    part_of_speech      VARCHAR(30),
    topic_id            UUID REFERENCES topics(id) ON DELETE SET NULL,
    example_ko          TEXT,
    example_translation TEXT,
    grammar_note        VARCHAR(255),             -- пометка на обороте карточки
    audio_url           VARCHAR(500),
    image_url           VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_words_topic ON words(topic_id);

-- слова, вводимые уроком
CREATE TABLE lesson_words (
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    word_id   UUID NOT NULL REFERENCES words(id)   ON DELETE CASCADE,
    PRIMARY KEY (lesson_id, word_id)
);

-- SRS-состояние слова у пользователя (SM-2)
CREATE TABLE user_words (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    word_id      UUID         NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    ease_factor  NUMERIC(4,2) NOT NULL DEFAULT 2.50 CHECK (ease_factor >= 1.30),
    interval_days INTEGER     NOT NULL DEFAULT 0,
    repetitions  INTEGER      NOT NULL DEFAULT 0,
    due_date     DATE         NOT NULL DEFAULT CURRENT_DATE,
    level        SMALLINT     NOT NULL DEFAULT 0 CHECK (level BETWEEN 0 AND 5), -- звёзды
    is_difficult BOOLEAN      NOT NULL DEFAULT FALSE,
    added_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, word_id)
);
CREATE INDEX idx_user_words_due ON user_words(user_id, due_date); -- выборка "к повторению"

-- пользовательские колоды
CREATE TABLE decks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE deck_words (
    deck_id UUID NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    word_id UUID NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    PRIMARY KEY (deck_id, word_id)
);

CREATE TABLE review_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode        review_mode NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    total       SMALLINT    NOT NULL DEFAULT 0,
    correct     SMALLINT    NOT NULL DEFAULT 0,
    xp_earned   SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_review_sessions_user ON review_sessions(user_id, started_at);

CREATE TABLE review_answers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID        NOT NULL REFERENCES review_sessions(id) ON DELETE CASCADE,
    word_id     UUID        NOT NULL REFERENCES words(id)           ON DELETE CASCADE,
    quality     SMALLINT    NOT NULL CHECK (quality BETWEEN 0 AND 5), -- оценка SM-2
    is_correct  BOOLEAN     NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ========================= MEDIA CONTEXT =============================

CREATE TABLE native_speakers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    bio        TEXT
);

CREATE TABLE media_clips (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind          clip_kind   NOT NULL,
    speaker_id    UUID REFERENCES native_speakers(id) ON DELETE SET NULL,
    word_id       UUID REFERENCES words(id)           ON DELETE SET NULL, -- для kind=WORD
    video_url     VARCHAR(500),
    audio_url     VARCHAR(500),
    thumbnail_url VARCHAR(500),
    duration_ms   INTEGER,
    is_published  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_media_clips_kind ON media_clips(kind) WHERE is_published;

ALTER TABLE stories
    ADD CONSTRAINT fk_stories_clip FOREIGN KEY (clip_id)
    REFERENCES media_clips(id) ON DELETE SET NULL;

CREATE TABLE subtitles (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clip_id  UUID        NOT NULL REFERENCES media_clips(id) ON DELETE CASCADE,
    lang     VARCHAR(10) NOT NULL,                 -- 'ko' | 'ru' | 'en'
    position SMALLINT    NOT NULL,
    text     TEXT        NOT NULL,
    start_ms INTEGER     NOT NULL,
    end_ms   INTEGER     NOT NULL,
    UNIQUE (clip_id, lang, position)
);

-- лента Immerse: просмотры/лайки
CREATE TABLE user_clip_views (
    user_id    UUID        NOT NULL REFERENCES users(id)       ON DELETE CASCADE,
    clip_id    UUID        NOT NULL REFERENCES media_clips(id) ON DELETE CASCADE,
    watched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    liked      BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, clip_id)
);

-- ===================== GAMIFICATION CONTEXT ==========================

CREATE TABLE levels (
    level  SMALLINT PRIMARY KEY,
    min_xp INTEGER      NOT NULL UNIQUE,
    title  VARCHAR(100) NOT NULL
);

CREATE TABLE xp_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount     SMALLINT    NOT NULL,
    source     xp_source   NOT NULL,
    source_id  UUID,                               -- id урока/сессии и т.п.
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_xp_events_user_date ON xp_events(user_id, created_at);

CREATE TABLE achievements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL UNIQUE,
    title       VARCHAR(150) NOT NULL,
    description TEXT,
    icon        VARCHAR(50),
    condition   JSONB        NOT NULL DEFAULT '{}' -- {type:'words_learned', value:100}
);

CREATE TABLE user_achievements (
    user_id        UUID        NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    achievement_id UUID        NOT NULL REFERENCES achievements(id) ON DELETE CASCADE,
    earned_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, achievement_id)
);

CREATE TABLE streaks (
    user_id          UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current          INTEGER NOT NULL DEFAULT 0,
    longest          INTEGER NOT NULL DEFAULT 0,
    last_active_date DATE
);

-- агрегат по дню: цель дня, график XP за неделю, лимиты Free
CREATE TABLE daily_activity (
    user_id           UUID     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_date     DATE     NOT NULL,
    xp_earned         INTEGER  NOT NULL DEFAULT 0,
    lessons_completed SMALLINT NOT NULL DEFAULT 0,
    reviews_done      SMALLINT NOT NULL DEFAULT 0,
    goal_xp           SMALLINT NOT NULL DEFAULT 20,
    PRIMARY KEY (user_id, activity_date)
);

-- ======================== NOTIFICATIONS ==============================

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       notification_type NOT NULL,
    title      VARCHAR(200)      NOT NULL,
    body       TEXT,
    is_read    BOOLEAN           NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ       NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read);

-- ======================== BILLING CONTEXT ============================

CREATE TABLE plans (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(50)   NOT NULL UNIQUE,   -- 'pro_month', 'pro_year', 'pro_lifetime'
    name         VARCHAR(100)  NOT NULL,
    billing_interval plan_interval NOT NULL,
    price_cents  INTEGER       NOT NULL,
    currency     CHAR(3)       NOT NULL DEFAULT 'USD',
    is_active    BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE subscriptions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id            UUID         NOT NULL REFERENCES plans(id),
    status             sub_status   NOT NULL DEFAULT 'ACTIVE',
    provider           pay_provider NOT NULL,
    provider_sub_id    VARCHAR(255),
    current_period_end TIMESTAMPTZ,               -- NULL для lifetime
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriptions_user ON subscriptions(user_id, status);

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id     UUID           REFERENCES subscriptions(id) ON DELETE SET NULL,
    user_id             UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_cents        INTEGER        NOT NULL,
    currency            CHAR(3)        NOT NULL DEFAULT 'USD',
    status              payment_status NOT NULL DEFAULT 'PENDING',
    provider_payment_id VARCHAR(255),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_user ON payments(user_id);

-- ========================== SEED (минимум) ===========================

INSERT INTO levels (level, min_xp, title) VALUES
 (1, 0, '초보 — Новичок'), (2, 100, '학생 — Ученик'), (3, 300, '탐험가 — Исследователь'),
 (4, 700, '달인 — Мастер'), (5, 1500, '호랑이 — Тигр');

INSERT INTO plans (code, name, billing_interval, price_cents) VALUES
 ('pro_month',    'Pro / месяц',    'MONTH',    999),
 ('pro_year',     'Pro / год',      'YEAR',     7900),
 ('pro_lifetime', 'Pro / навсегда', 'LIFETIME', 14900);
