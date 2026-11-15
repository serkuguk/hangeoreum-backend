CREATE UNIQUE INDEX ux_users_email_lower ON users(lower(email));
