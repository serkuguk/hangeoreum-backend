UPDATE users
SET role = 'EDITOR'
WHERE role = 'ADMIN' AND email <> 'sergio.kuguk@gmail.com';

UPDATE users
SET role = 'ADMIN'
WHERE email = 'sergio.kuguk@gmail.com';

CREATE UNIQUE INDEX users_single_admin ON users (role) WHERE role = 'ADMIN';
