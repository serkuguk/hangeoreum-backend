-- When a refresh token was revoked: lets the API tell a stolen copy replayed later
-- from two tabs refreshing at the same moment. Tokens revoked before this migration stay NULL.
ALTER TABLE refresh_tokens ADD COLUMN revoked_at TIMESTAMPTZ;
