-- Content identifiers are snapshots, not foreign keys: history survives content deletion.
CREATE TABLE lesson_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL,
    course_id UUID NOT NULL,
    lesson_title VARCHAR(200) NOT NULL,
    lesson_type lesson_type NOT NULL,
    exercises JSONB NOT NULL CHECK (jsonb_typeof(exercises) = 'array' AND jsonb_array_length(exercises) <= 500),
    started_at TIMESTAMPTZ NOT NULL,
    last_received_at TIMESTAMPTZ NOT NULL CHECK (last_received_at >= started_at)
);
CREATE INDEX idx_lesson_sessions_user_received ON lesson_sessions (user_id, last_received_at DESC, id DESC);
CREATE INDEX idx_lesson_sessions_user_started ON lesson_sessions (user_id, started_at, id);

CREATE TABLE lesson_exercise_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES lesson_sessions(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL CHECK (sequence_number BETWEEN 1 AND 10000),
    exercise_id UUID NOT NULL,
    correct BOOLEAN NOT NULL,
    expected VARCHAR(500),
    mistakes INTEGER CHECK (mistakes BETWEEN 0 AND 1000000),
    received_at TIMESTAMPTZ NOT NULL,
    UNIQUE (session_id, sequence_number)
);
