-- Sign-in now requires a confirmed email. Accounts that exist before this migration are
-- treated as confirmed so nobody is locked out.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
UPDATE users SET email_verified_at = created_at;
