-- Topic icon can now be an uploaded image URL (/media/topics/<uuid>.png = 54 chars), not only an emoji.
ALTER TABLE topics ALTER COLUMN icon TYPE VARCHAR(255);
